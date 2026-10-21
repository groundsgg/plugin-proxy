#!/usr/bin/env python3
"""Build a test-only plugin JAR and start two isolated Velocity processes.
Usage: live-harness.py /absolute/path/to/velocity.jar [working-directory]
Only the descriptor is replaced; the production listener/evaluator classes are unchanged.
"""
import pathlib,sys,json,zipfile,subprocess,hashlib,os,shutil,time
root=pathlib.Path(__file__).resolve().parents[2]
work=pathlib.Path(sys.argv[2] if len(sys.argv)>2 else '/tmp/grounds-motd-proxies');work.mkdir(parents=True,exist_ok=True)
source=root/'velocity/build/libs/plugin-proxy-velocity.jar'
plugin=work/'motd-test-harness.jar'
with zipfile.ZipFile(source) as src,zipfile.ZipFile(plugin,'w',zipfile.ZIP_DEFLATED) as out:
 for n in src.namelist():
  if n!='velocity-plugin.json' and not n.endswith('/'):out.writestr(n,src.read(n))
 classes=root/'velocity/build/classes/kotlin/test'
 for f in classes.rglob('ServerListLiveHarness*.class'):out.write(f,f.relative_to(classes))
 out.writestr('velocity-plugin.json',json.dumps({'id':'motd-validation','name':'MOTD validation harness','version':'0.1.0','main':'gg.grounds.proxy.velocity.motd.ServerListLiveHarness','authors':[]}))
fixtures=json.loads((root/'velocity/src/test/resources/server-list/contract.json').read_text())
shared=work/'shared.json';shared.write_text(json.dumps({'networkPlayers':8,'motd':{'text':'<gold>{{region}} {{players}}/{{max}}','serverList':fixtures[2]['template']},'maintenance':None}))
java=str(pathlib.Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java')
processes=[];live=[]
for name,port,region,local in [('a',25611,'eu',3),('b',25612,'na',5)]:
 d=work/name;d.mkdir(exist_ok=True);(d/'plugins').mkdir(exist_ok=True);(d/'plugins/bStats').mkdir(exist_ok=True);(d/'plugins/bStats/config.txt').write_text('enabled=false\n');(d/'plugins/motd-test-harness.jar').write_bytes(plugin.read_bytes())
 (d/'forwarding.secret').write_text('local-validation-only')
 (d/'velocity.toml').write_text(f'''config-version = "2.7"
bind = "127.0.0.1:{port}"
motd = "Test base"
show-max-players = 500
online-mode = false
force-key-authentication = false
player-info-forwarding-mode = "NONE"
forwarding-secret-file = "forwarding.secret"
announce-forge = false
kick-existing-players = false
ping-passthrough = "DISABLED"
sample-players-in-ping = true
enable-player-address-logging = false
[servers]
lobby = "127.0.0.1:9"
factions = "127.0.0.1:9"
minigames = "127.0.0.1:9"
try = []
[advanced]
compression-threshold = 256
compression-level = -1
login-ratelimit = 0
connection-timeout = 5000
read-timeout = 30000
haproxy-protocol = false
tcp-fast-open = false
bungee-plugin-message-channel = true
show-ping-requests = false
failover-on-unexpected-server-disconnect = true
announce-proxy-commands = false
log-command-executions = false
log-player-connections = false
accepts-transfers = false
''')
 log=open(d/'proxy.log','wb')
 p=subprocess.Popen([java,'-Xms128m','-Xmx256m',f'-Dmotd.harness.document={shared}',f'-Dmotd.harness.region={region}',f'-Dmotd.harness.local={local}','-jar',str(pathlib.Path(sys.argv[1]).resolve())],cwd=d,stdout=log,stderr=subprocess.STDOUT,stdin=subprocess.PIPE)
 live.append(p)
 processes.append({'name':name,'pid':p.pid,'address':f'127.0.0.1:{port}','localPlayers':local,'region':region})
(work/'processes.json').write_text(json.dumps(processes,indent=2));print(json.dumps({'work':str(work),'velocitySha256':hashlib.sha256(pathlib.Path(sys.argv[1]).read_bytes()).hexdigest(),'processes':processes},indent=2))

if '--stay' in sys.argv:
 sys.stdout.flush()
 try:
  while True:time.sleep(1)
 except KeyboardInterrupt:
  for p in processes:
   try:os.kill(p['pid'],15)
   except ProcessLookupError:pass
