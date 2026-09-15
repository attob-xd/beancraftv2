"""Serve javascript/ with caching switched off.

    python tools/serve.py [port]

Python's http.server sends no Cache-Control at all, and Chrome then applies *heuristic*
caching: it may reuse a response without revalidating. classes.js is referenced from
index.html as a plain relative path with no version query, so a cache-busting query on the
page URL does not reach it - the browser keeps serving the previous build's script while the
page itself reloads.

That is not a cosmetic annoyance. It cost a full debugging cycle: a fix was verified present
in build/classes and absent from the trimmed jar, the page still threw the exact error the
fix removed, and the reason was simply that the browser was running the previous build.

no-store on every response makes a reload mean what it says.
"""
import functools
import http.server
import os
import sys


class NoCacheHandler(http.server.SimpleHTTPRequestHandler):

    def end_headers(self):
        self.send_header('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0')
        self.send_header('Pragma', 'no-cache')
        self.send_header('Expires', '0')
        super().end_headers()

    def log_message(self, fmt, *args):
        pass                                          # the build log is noisy enough


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8088
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir, 'javascript')
    handler = functools.partial(NoCacheHandler, directory=os.path.abspath(root))
    print('serving %s on http://localhost:%d (no-store)' % (os.path.abspath(root), port))
    http.server.ThreadingHTTPServer(('', port), handler).serve_forever()


if __name__ == '__main__':
    main()
