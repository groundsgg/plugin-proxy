package gg.grounds.proxy.velocity.motd

import gg.grounds.grpc.config.ConfigAdminServiceGrpc
import gg.grounds.grpc.config.ConfigServiceGrpc
import gg.grounds.grpc.config.DeleteDocumentRequest
import gg.grounds.grpc.config.GetDocumentRequest
import gg.grounds.grpc.config.PutDocumentRequest
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.LoadBalancerRegistry
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.NameResolverRegistry
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.internal.DnsNameResolverProvider
import io.grpc.internal.PickFirstLoadBalancerProvider
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * The MOTD's home in service-config: one document, `motd/active`, under a fixed app and the
 * deployment's environment.
 *
 * Reads go through `ConfigService`, which any authenticated caller may use; writes go through
 * `ConfigAdminService`, which service-config restricts to admin service accounts and to writers
 * explicitly allowed for this app. A proxy that is not on that list can still show the MOTD — it
 * just cannot change it, and `/motd set` says so rather than failing silently.
 *
 * Deliberately not built on the shared `plugin-config` client: that one reads only, and attaches no
 * credential, so it cannot do either half of this against a service-config with auth enabled.
 */
class MotdConfigStore(
    private val app: String,
    private val env: String,
    private val channel: ManagedChannel,
) : MotdStore, AutoCloseable {

    /**
     * The stored MOTD, or null when none is set. Null is the normal state of a fresh network, not
     * an error — the caller then leaves Velocity's own MOTD alone.
     */
    override fun read(): MotdDocument? {
        val response =
            try {
                ConfigServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getDocument(
                        GetDocumentRequest.newBuilder()
                            .setApp(app)
                            .setEnv(env)
                            .setNamespace(NAMESPACE)
                            .setConfigKey(CONFIG_KEY)
                            .build()
                    )
            } catch (ex: StatusRuntimeException) {
                if (ex.status.code == Status.Code.NOT_FOUND) return null
                throw ex
            }
        return MotdDocument.fromJson(response.document.contentJson)
    }

    /** Stores [document] as the network's MOTD, replacing whatever was there. */
    override fun write(document: MotdDocument, updatedBy: String) {
        ConfigAdminServiceGrpc.newBlockingStub(channel)
            .withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
            .putDocument(
                PutDocumentRequest.newBuilder()
                    .setApp(app)
                    .setEnv(env)
                    .setNamespace(NAMESPACE)
                    .setConfigKey(CONFIG_KEY)
                    .setContentJson(document.toJson())
                    .setUpdatedBy(updatedBy)
                    .build()
            )
        // No expected_version: two operators racing on /motd is a coin flip either way, and a
        // rejected write that says "someone else changed it, try again" is worse in chat than the
        // second one simply winning. The dashboard, which can show the conflict, is where
        // optimistic concurrency earns its keep.
    }

    /** Removes the stored MOTD. Returns true when there was one to remove. */
    override fun clear(deletedBy: String): Boolean =
        ConfigAdminServiceGrpc.newBlockingStub(channel)
            .withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
            .deleteDocument(
                DeleteDocumentRequest.newBuilder()
                    .setApp(app)
                    .setEnv(env)
                    .setNamespace(NAMESPACE)
                    .setConfigKey(CONFIG_KEY)
                    .setDeletedBy(deletedBy)
                    .build()
            )
            .deleted

    override fun close() {
        channel.shutdown()
        if (!channel.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
            channel.shutdownNow()
        }
    }

    companion object {
        const val NAMESPACE = "motd"
        const val CONFIG_KEY = "active"

        private const val DEADLINE_SECONDS = 5L
        private const val SHUTDOWN_SECONDS = 3L

        /**
         * Where the projected ServiceAccount token is mounted. The kubelet rotates it well before
         * expiry, so it is read per call rather than held — a proxy that stays up longer than an
         * hour would otherwise start presenting an expired one.
         */
        private const val DEFAULT_TOKEN_PATH = "/var/run/secrets/grounds/token"

        fun open(app: String, env: String, target: String): MotdConfigStore {
            // Velocity loads each plugin in its own classloader, and gRPC's service-loader
            // discovery finds nothing there. Registering both providers by hand is what makes a
            // `dns:///` target resolvable from inside a shaded plugin jar; without it the channel
            // comes up and every call fails with UNAVAILABLE.
            NameResolverRegistry.getDefaultRegistry().register(DnsNameResolverProvider())
            LoadBalancerRegistry.getDefaultRegistry().register(PickFirstLoadBalancerProvider())

            val channel =
                ManagedChannelBuilder.forTarget(target)
                    .usePlaintext()
                    .intercept(BearerTokenInterceptor(::readToken))
                    .build()
            return MotdConfigStore(app, env, channel)
        }

        private fun readToken(): String? {
            val path = Path.of(System.getenv("GROUNDS_TOKEN_FILE") ?: DEFAULT_TOKEN_PATH)
            return try {
                if (Files.exists(path)) Files.readString(path).trim().ifEmpty { null } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Attaches the projected ServiceAccount token, which service-config verifies against the
     * cluster's JWKS and expects to carry the `grounds-services` audience.
     *
     * A missing token is passed through unauthenticated on purpose: locally there is no projected
     * volume and service-config runs with auth off, and in the cluster the server rejecting the
     * call is a clearer failure than the client refusing to make it.
     */
    internal class BearerTokenInterceptor(private val token: () -> String?) : ClientInterceptor {
        override fun <ReqT : Any, RespT : Any> interceptCall(
            method: MethodDescriptor<ReqT, RespT>,
            callOptions: CallOptions,
            next: Channel,
        ): ClientCall<ReqT, RespT> =
            object :
                ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                    next.newCall(method, callOptions)
                ) {
                override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                    token()?.let { headers.put(AUTHORIZATION, "Bearer $it") }
                    super.start(responseListener, headers)
                }
            }

        private companion object {
            val AUTHORIZATION: Metadata.Key<String> =
                Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
        }
    }
}
