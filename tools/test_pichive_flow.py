import urllib.request, urllib.parse, re, json

input_url = 'https://sn.hotlinger.com/iframe.php?v=b8e941c73733024b9ad81aef2d449d61'
actual_url = input_url.replace('sn.hotlinger.com', 'four.pichive.online')

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Referer': 'https://selcukflix.app/'
}

req = urllib.request.Request(actual_url, headers=headers)
with urllib.request.urlopen(req, timeout=5) as r:
    html = r.read().decode('utf-8')

m = re.search(r'openPlayer\s*\(\s*[\'"]([^\'"]+)[\'"]', html)
if not m:
    print('openPlayer not found!')
    exit(1)

playlist = m.group(1)
v = urllib.parse.parse_qs(urllib.parse.urlparse(actual_url).query).get('v', [''])[0]

source_url = f'https://four.pichive.online/source2.php?v={urllib.parse.quote(playlist)}'
source_headers = {
    'User-Agent': headers['User-Agent'],
    'Referer': f'https://four.pichive.online/iframe.php?v={v}',
    'X-Requested-With': 'XMLHttpRequest'
}

req2 = urllib.request.Request(source_url, headers=source_headers)
with urllib.request.urlopen(req2, timeout=5) as r2:
    data = json.loads(r2.read().decode('utf-8'))

print('State:', data.get('state'))
file_url = data['playlist'][0]['sources'][0]['file']
print('Stream file URL:', file_url[:120], '...')

# probe media
stream_req = urllib.request.Request(file_url, headers={'User-Agent': headers['User-Agent'], 'Referer': 'https://four.pichive.online/'})
with urllib.request.urlopen(stream_req, timeout=5) as r3:
    print('Stream HTTP status:', r3.status)
    content = r3.read().decode('utf-8')
    print('M3U8 preview:\n', content[:200])
