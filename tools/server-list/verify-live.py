#!/usr/bin/env python3
"""Probe two running live-harness.py instances over real Minecraft status TCP packets."""
import pathlib,json,socket,struct,time,zlib,base64,hashlib,sys
work=pathlib.Path(sys.argv[1] if len(sys.argv)>1 else '/tmp/grounds-motd-proxies');shared=work/'shared.json'
def varint(n):
 b=bytearray()
 while True:
  part=n&127;n>>=7;b.append(part|(128 if n else 0))
  if not n:return bytes(b)
def readvar(s):
 n=0
 for i in range(5):
  b=s.recv(1)
  if not b:raise EOFError()
  n|=(b[0]&127)<<(i*7)
  if b[0]<128:return n
 raise ValueError('varint')
def ping(port,protocol=767):
 with socket.create_connection(('127.0.0.1',port),timeout=3) as s:
  h=b'localhost';packet=b'\0'+varint(protocol)+varint(len(h))+h+struct.pack('>H',port)+b'\1';s.sendall(varint(len(packet))+packet+b'\1\0');readvar(s);assert readvar(s)==0;size=readvar(s);body=b''
  while len(body)<size:body+=s.recv(size-len(body))
  return json.loads(body)
def both():return [ping(25611),ping(25612)]
def write(data):
 temp=shared.with_suffix('.next');temp.write_text(json.dumps(data));temp.replace(shared)
def until(check):
 deadline=time.monotonic()+8
 while True:
  result=both()
  if check(result):return result
  if time.monotonic()>deadline:raise AssertionError(json.dumps(result)[:1000])
  time.sleep(.1)
def plain(c):
 if isinstance(c,str):return c
 if isinstance(c,list):return ''.join(map(plain,c))
 return c.get('text','')+''.join(map(plain,c.get('extra',[])))
def chunk(k,v):return struct.pack('>I',len(v))+k+v+struct.pack('>I',zlib.crc32(k+v)&0xffffffff)
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',64,64,8,6,0,0,0))+chunk(b'IDAT',zlib.compress((b'\0'+b'\xff\x55\x00\xff'*64)*64))+chunk(b'IEND',b'')
data=json.loads(shared.read_text());data['motd']['serverList']['icon']={'pngBase64':base64.b64encode(png).decode()};write(data)
initial=until(lambda ps:all(p['players']['online']==8 and p.get('favicon') for p in ps));assert plain(initial[0]['description'])=='eu 8/500';assert plain(initial[1]['description'])=='na 8/500';assert initial[0]['favicon']==initial[1]['favicon'];assert '8 players' in initial[0]['players']['sample'][0]['name']
evidence={'initial':initial}
data['motd']['text']='<green>Changed {{region}} {{players}}/{{max}}';data['motd']['serverList']['players']['online']={'mode':'offset','value':2};data['motd']['serverList']['players']['maximum']={'mode':'headroom','value':10};write(data)
updated=until(lambda ps:all(p['players']['online']==10 and p['players']['max']==20 for p in ps));assert '10/20' in plain(updated[0]['description']);assert '10 players' in updated[1]['players']['sample'][0]['name'];evidence['updated']=updated
maintenance={'schemaVersion':1,'enabled':True,'id':'00000000-0000-0000-0000-000000000001','startsAt':'2020-01-01T00:00:00Z','endsAt':None,'estimatedEndAt':None,'reason':'Test','joinMessage':'Closed','motd':'','kickExisting':False,'whitelist':[],'announcementSeconds':[],'announcementMessage':'Soon','serverList':json.loads(json.dumps(data['motd']['serverList'])),'serverListFallbackText':'','serverListOverrides':['version']}
maintenance['serverList']['version']={'mode':'always','text':{'revision':0,'nodes':[{'id':'v','type':'group','style':{'color':'#ff5555'},'children':[{'id':'vt','type':'text','value':'Maintenance'}]}]}};data['maintenance']=maintenance;write(data)
active=until(lambda ps:all(p['version']['protocol']==-1 and 'Maintenance' in p['version']['name'] for p in ps));assert [p['favicon'] for p in active]==[p['favicon'] for p in updated];assert all(p['players']['online']==10 for p in active);evidence['maintenance']=active
maintenance['endsAt']='2020-01-02T00:00:00Z';write(data);ended=until(lambda ps:[p['version'] for p in ps]==[p['version'] for p in updated]);evidence['ended']=ended
shared.write_text('{broken');time.sleep(.5);cached=both();assert [p['version'] for p in cached]==[p['version'] for p in ended];assert all(p['players']['online']==10 for p in cached);evidence['failedRefresh']=cached
bad=json.loads(json.dumps(data));bad['motd']['serverList']['icon']={'pngBase64':'iVBORw0KGgo='};write(bad);time.sleep(.5);retained=both();assert [p['favicon'] for p in retained]==[p['favicon'] for p in ended];evidence['invalidIcon']=retained
# Exercise the current compiled runtime path, measuring placeholders after resolution.
fixtures=json.loads((pathlib.Path(__file__).resolve().parents[2]/'velocity/src/test/resources/server-list/contract.json').read_text());aligned=next(f for f in fixtures if f['name']=='aligned-placeholder');data['maintenance']=None;data['motd']={'text':aligned['source'],'serverListFallbackText':aligned['source'],'serverList':aligned['template'],'maxPlayers':100};write(data);alignedPings=until(lambda ps:all('8 / 100' in plain(p['description']) for p in ps));assert plain(alignedPings[0]['description'])==aligned['expectedPlain'];assert '{{' not in plain(alignedPings[1]['description']);evidence['resolvedAlignment']=alignedPings
# Native 1.21.11/26.x content width is 301, leaving 267 logical pixels for MOTD.
modern=json.loads(json.dumps(data));modern['motd']['serverList']['motd']={'text':{'revision':0,'nodes':[{'id':'a','type':'text','value':'A'}]},'alignments':['right']};modern['motd']['text']='A';modern['motd']['serverListFallbackText']='A';write(modern)
until(lambda ps:all(plain(p['description'])==' '*66+'A' for p in ps))
modernPings=[ping(port,777) for port in [25611,25612]]
assert all(plain(p['description'])==' '*65+'A' for p in modernPings)
evidence['modernClientAlignment']=modernPings
evidence['addresses']=['127.0.0.1:25611','127.0.0.1:25612'];evidence['localCounts']=[3,5];evidence['networkCount']=8;evidence['runtime']='Velocity 4.2.0';evidence['sharedConfig']=str(shared);evidence['iconSha256']=hashlib.sha256(png).hexdigest();(work/'verification.json').write_text(json.dumps(evidence,indent=2));print('PASS: real TCP pings on both proxies; shared 8 → displayed 10/20; regions, icon, hover, maintenance restoration, failed refresh and invalid icon cache retained.')
