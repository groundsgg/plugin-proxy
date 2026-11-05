import com.velocitypowered.api.event.*;
import com.velocitypowered.api.event.connection.*;
import com.velocitypowered.api.event.player.*;
import com.velocitypowered.api.network.*;
import com.velocitypowered.api.plugin.*;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.messages.*;
import com.velocitypowered.api.scheduler.*;
import com.velocitypowered.proxy.event.VelocityEventManager;
import gg.grounds.i18n.Translations;
import gg.grounds.proxy.velocity.welcome.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.ClickEvent;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;

public final class WelcomeDispatchProbe {
 static <T> T fake(Class<T> type, BiFunction<String,Object[],Object> fn) {
  return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->switch(m.getName()) {
   case "hashCode" -> System.identityHashCode(p);case "equals" -> p==a[0];case "toString" -> type.getSimpleName();default -> fn.apply(m.getName(),a);
  }));
 }
 static void check(boolean value,String reason){if(!value)throw new IllegalStateException(reason);}
 static boolean install(Component c){return ClickEvent.openUrl(WelcomeMessage.INSTALL_URL).equals(c.clickEvent())||c.children().stream().anyMatch(WelcomeDispatchProbe::install);}
 public static final class Blocker {
  final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  @Subscribe(order=PostOrder.FIRST)
  public void block(PluginMessageEvent event) throws InterruptedException {entered.countDown();release.await(10,TimeUnit.SECONDS);}
 }
 public static void main(String[] args) throws Exception {
  ExecutorService workers=Executors.newFixedThreadPool(2);
  var description=fake(PluginDescription.class,(n,a)->switch(n){case "getId" -> "welcome-dispatch-probe";case "getVersion","getName" -> Optional.of("probe");default -> null;});
  var plugin=fake(PluginContainer.class,(n,a)->switch(n){case "getDescription" -> description;case "getExecutorService" -> workers;default -> Optional.empty();});
  var manager=new VelocityEventManager(fake(PluginManager.class,(n,a)->Optional.of(plugin)));
  var messages=Translations.forBundle("gg.grounds.proxy.messages",WelcomeDispatchProbe.class.getClassLoader());
  var sent=new CopyOnWriteArrayList<Component>();var requested=new CopyOnWriteArrayList<Key>();
  var player=fake(Player.class,(n,a)->switch(n){case "getUniqueId" -> UUID.fromString("a87fa68d-7d18-43ad-82c2-219d447a3867");case "getUsername" -> "Alex";case "getHandshakeIntent" -> HandshakeIntent.LOGIN;case "getProtocolVersion" -> ProtocolVersion.MINECRAFT_1_21;case "isActive" -> true;case "get" -> Optional.of(Locale.ENGLISH);case "requestCookie" -> {requested.add((Key)a[0]);yield null;}case "sendMessage" -> {sent.add((Component)a[0]);yield null;}default -> null;});
  var task=fake(ScheduledTask.class,(n,a)->null);
  var listener=new WelcomeListener(messages,new WelcomeMessage(messages,new WelcomeLinks("https://grnds.link/discord","https://example.com/grounds/tiktok","https://example.com/grounds/shop")),work->{work.run();return task;},p->true,p->true,work->{throw new IllegalStateException("Modern client entered legacy grace");});
  Blocker blocker=new Blocker();
  try {
   // Register the blocking async FIRST handler first, so a mere default/FIRST observer cannot win.
   manager.registerInternally(plugin,blocker);manager.registerInternally(plugin,listener);
   manager.fire(new PostLoginEvent(player)).get(2,TimeUnit.SECONDS);
   manager.fire(new ServerPostConnectEvent(player,null)).get(2,TimeUnit.SECONDS);
   check(requested.equals(List.of(Key.key("grounds:welcome_ready"))),"Missing play acknowledgement");
   var event=new PluginMessageEvent(player,fake(ServerConnection.class,(n,a)->null),MinecraftChannelIdentifier.from("voicechat:request_secret"),new byte[]{0,0,0,20});
   var forwarding=manager.fire(event);
   check(blocker.entered.await(2,TimeUnit.SECONDS),"Async handler never started");
   check(!forwarding.isDone(),"The simulated async forwarding was not blocked");
   manager.fire(new CookieReceiveEvent(player,Key.key("grounds:welcome_ready"),null)).get(2,TimeUnit.SECONDS);
   check(sent.size()==1,"No prompt welcome while later async forwarding is blocked");
   check(!install(sent.get(0)),"Cookie reply overtook the installed-mod observer");
   check(event.getResult().isAllowed(),"Observer consumed the actual handshake");
   blocker.release.countDown();forwarding.get(2,TimeUnit.SECONDS);
   manager.fire(new CookieReceiveEvent(player,Key.key("grounds:welcome_ready"),null)).get(2,TimeUnit.SECONDS);
   check(sent.size()==1,"Duplicate ready reply repeated welcome");
   System.out.println("actualVelocityDispatch=ok syncObserverBeforeAsync=ok playCookie=ok immediateScheduler=ok voiceFalsePositive=none duplicateReply=ignored forwarding=unchanged");
  } finally {blocker.release.countDown();listener.close();workers.shutdownNow();}
 }
}
