#!/usr/bin/env python3
"""Local Xtream fixture for real app/URLSession/AVPlayer integration, never production."""
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path
from urllib.parse import urlparse, parse_qs
import argparse, base64, json, time
ASSETS = Path(__file__).resolve().parents[1]/'apps/android/app/src/androidTest/assets'
HLS = Path(__file__).resolve().parents[1]/'shared/test-fixtures/hls'
class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_): pass  # Requests can contain credentials. Never log URLs.
    def do_GET(self):
        url=urlparse(self.path); query=parse_qs(url.query)
        port=self.server.server_address[1]; base=f'http://127.0.0.1:{port}'
        if url.path.endswith('player_api.php'):
            if query.get('username') != ['fixture'] or query.get('password') != ['fixture']:
                return self.respond(403,b'{}','application/json')
            action=query.get('action',[''])[0]; now=int(time.time())
            category=[{'category_id':'1','category_name':'Fixture category'}]
            def item(id,name,kind):
                return {('series_id' if kind=='series' else 'stream_id'):id,'name':name,'category_id':'1','stream_icon':base+'/poster.jpg','cover':base+'/poster.jpg','backdrop_path':[base+'/poster.jpg'],'container_extension':'mp4','genre':'Drama','year':'2026','rating':'8.0','language':'English','added':now}
            bodies={
                '':{'user_info':{'auth':1,'status':'Active'}},
                'get_live_streams':[item(1,'Fixture News','live'),item(5,'Fixture Sports','live')],
                'get_vod_streams':[item(2,'Fixture Movie','movie')],
                'get_series':[item(3,'Fixture Series','series')],
                'get_vod_info':{'info':{'plot':'Fixture movie plot','cast':'Fixture Cast','director':'Fixture Director','genre':'Drama','rating':'8.0','year':'2026','duration':'00:00:01','backdrop_path':[base+'/poster.jpg']},'movie_data':{'container_extension':'mp4'}},
                'get_series_info':{'info':{'plot':'Fixture series plot','genre':'Drama'},'episodes':{'1':[{'id':4,'title':'Pilot','episode_num':1,'container_extension':'mp4','info':{'plot':'First episode overview','duration_secs':1}},{'id':6,'title':'Second episode','episode_num':2,'container_extension':'mp4'}],'2':[{'id':7,'title':'New season','episode_num':1,'container_extension':'mp4'}]}},
                'get_short_epg':{'epg_listings':[{'title':base64.b64encode(t.encode()).decode(),'start_timestamp':s,'stop_timestamp':e} for t,s,e in [('News now',now-600,now+600),('News next',now+600,now+1200)]]}
            }
            body=category if action.endswith('_categories') else bodies.get(action,[])
            return self.respond(200,json.dumps(body).encode(),'application/json')
        if url.path.endswith('.m3u8'):
            return self.respond(200,(HLS/'index.m3u8').read_bytes(),'application/vnd.apple.mpegurl')
        filename=Path(url.path).name
        if filename=='init.mp4' or (filename.startswith('segment-') and filename.endswith('.m4s')):
            # Select only a basename under the fixture directory; no user path traversal.
            media=HLS/filename
            if not media.is_file(): return self.respond(404,b'','text/plain')
            data=media.read_bytes(); content='video/mp4'
        else:
            name='poster.jpg' if url.path.endswith('.jpg') else 'sample.ts' if url.path.endswith('.ts') else 'sample.mp4'
            data=(ASSETS/name).read_bytes(); content='image/jpeg' if name.endswith('jpg') else 'video/mp2t' if name.endswith('ts') else 'video/mp4'
        value=self.headers.get('Range')
        if value:
            try:
                a,b=value.removeprefix('bytes=').split('-'); start=int(a or 0); end=min(int(b) if b else len(data)-1,len(data)-1)
                if start> end: return self.respond(416,b'',content)
                return self.respond(206,data[start:end+1],content,{'Content-Range':f'bytes {start}-{end}/{len(data)}'})
            except ValueError: return self.respond(416,b'',content)
        self.respond(200,data,content)
    def respond(self,status,data,content,headers=None):
        self.send_response(status); self.send_header('Content-Type',content); self.send_header('Content-Length',str(len(data))); self.send_header('Accept-Ranges','bytes')
        for k,v in (headers or {}).items(): self.send_header(k,v)
        self.end_headers()
        try: self.wfile.write(data)
        except (BrokenPipeError,ConnectionResetError): pass
if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('--port',type=int,default=18765); parser.add_argument('--port-file'); args=parser.parse_args()
    server=ThreadingHTTPServer(('127.0.0.1',args.port),Handler)
    if args.port_file: Path(args.port_file).write_text(str(server.server_address[1]))
    print(f'Fixture listening on 127.0.0.1:{server.server_address[1]}',flush=True); server.serve_forever()
