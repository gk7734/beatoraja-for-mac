"""Local HTTP integration test; downloads metadata only, preserves valid caches on failure."""
import functools, hashlib, http.server, json, pathlib, subprocess, sys, threading
root = pathlib.Path(sys.argv[1]).resolve()
app = pathlib.Path(sys.argv[2]).resolve() / 'Contents'
root.mkdir(parents=True, exist_ok=True)
server_root = root / 'server'; server_root.mkdir(exist_ok=True)
(server_root/'header.json').write_text(json.dumps({'name':'Test Difficulty','symbol':'T','data_url':'data.json','level_order':['1']}))
(server_root/'data.json').write_text(json.dumps([{'title':'Test Song','md5':'0'*32,'level':'1'}]))
handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=str(server_root))
server = http.server.ThreadingHTTPServer(('127.0.0.1',0),handler)
threading.Thread(target=server.serve_forever,daemon=True).start()
url = f'http://127.0.0.1:{server.server_port}/header.json'
config = json.loads((app/'app/defaults/config_sys.json').read_text()); config['tablepath']='table';config['tableURL']=[url]
(root/'config_sys.json').write_text(json.dumps(config))
args=[str(app/'runtime/Contents/Home/bin/java'),'-cp',str(app/'app/beatoraja.jar'),'bms.player.beatoraja.MacMaintenance','--tables']
def run():
 with (root/'test.log').open('a') as log:return subprocess.run(args,cwd=root,stdout=log,stderr=log,timeout=40)
try:
 assert run().returncode==0
 cache=root/'table'/(hashlib.sha256(url.encode()).hexdigest()+'.bmt');before=cache.read_bytes()
 report=json.loads((root/'table-update-result.json').read_text());assert report['results'][0]['success']
 (server_root/'header.json').write_text('invalid json')
 assert run().returncode!=0
 assert cache.read_bytes()==before
 report=json.loads((root/'table-update-result.json').read_text());assert not report['results'][0]['success']
 print('PASS: metadata download, URL cache, failure report, previous cache preserved')
finally:server.shutdown()
