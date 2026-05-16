#!/usr/bin/env python3
"""GUI management server - serves index.html and proxies API calls to nodes."""

import http.server
import json
import os
import subprocess
import urllib.request
from urllib.parse import urlparse, parse_qs

NAMING_SERVER = "http://172.20.0.2:8080"
NODE_PORT = 8080
SERVE_PORT = 5050
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))


def http_get(url, timeout=3):
    try:
        with urllib.request.urlopen(url, timeout=timeout) as r:
            return r.read().decode()
    except Exception:
        return None


def docker_inspect(container, fmt):
    try:
        r = subprocess.run(
            ["docker", "inspect", container, "--format", fmt],
            capture_output=True, text=True, timeout=5
        )
        return r.stdout.strip() if r.returncode == 0 else None
    except Exception:
        return None


def get_node_network():
    net = docker_inspect(
        "node-a",
        "{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}"
    )
    return net or "dist-lab3_dist-net"


def get_node_image():
    img = docker_inspect("node-a", "{{.Config.Image}}")
    return img or "dist-lab3-node-a"


def build_node_list():
    """Merges naming server nodes and running Docker containers into one list."""
    nodes_by_ip = {}

    # Source 1: naming server
    raw = http_get(f"{NAMING_SERVER}/naming/nodes")
    if raw:
        try:
            for node_id, ip in json.loads(raw).items():
                node = {"nodeId": node_id, "ip": ip, "status": "offline"}
                info = http_get(f"http://{ip}:{NODE_PORT}/node/info", timeout=2)
                if info:
                    try:
                        node.update(json.loads(info))
                        node["status"] = "online"
                    except Exception:
                        pass
                nodes_by_ip[ip] = node
        except Exception:
            pass

    # Source 2: running Docker node containers
    try:
        r = subprocess.run(
            ["docker", "ps", "--format", "{{.Names}}\t{{.Status}}"],
            capture_output=True, text=True, timeout=5
        )
        for line in r.stdout.strip().splitlines():
            parts = line.split("\t")
            if len(parts) < 2:
                continue
            name, status = parts[0].strip(), parts[1].strip()
            if not name.startswith("node-") or name == "naming-server":
                continue
            ip = docker_inspect(name, "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}")
            if not ip:
                continue
            if ip in nodes_by_ip:
                nodes_by_ip[ip]["containerName"] = name
            else:
                node = {"ip": ip, "containerName": name, "status": "starting"}
                info = http_get(f"http://{ip}:{NODE_PORT}/node/info", timeout=1)
                if info:
                    try:
                        node.update(json.loads(info))
                        node["status"] = "online"
                    except Exception:
                        pass
                nodes_by_ip[ip] = node
    except Exception:
        pass

    return list(nodes_by_ip.values())


class Handler(http.server.BaseHTTPRequestHandler):

    def log_message(self, fmt, *args):
        pass

    def send_json(self, code, data):
        body = json.dumps(data, ensure_ascii=False).encode()
        self._send(code, "application/json", body)

    def send_text(self, code, text):
        self._send(code, "text/plain; charset=utf-8", text.encode())

    def _send(self, code, ctype, body):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", len(body))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def do_GET(self):
        p = urlparse(self.path)
        path = p.path
        qs = parse_qs(p.query)

        if path in ("/", "/index.html"):
            try:
                with open(os.path.join(SCRIPT_DIR, "index.html"), "rb") as f:
                    body = f.read()
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", len(body))
                self.end_headers()
                self.wfile.write(body)
            except FileNotFoundError:
                self.send_response(404)
                self.end_headers()
            return

        if path == "/api/nodes":
            self._nodes()
        elif path == "/api/containers":
            self._containers()
        elif path.startswith("/api/logs/"):
            container = path[len("/api/logs/"):]
            lines = qs.get("lines", ["100"])[0]
            self._logs(container, lines)
        elif path.startswith("/api/node/"):
            rest = path[len("/api/node/"):]
            if rest.endswith("/files/local"):
                self._proxy(f"http://{rest[:-len('/files/local')]}:{NODE_PORT}/node/files/local")
            elif rest.endswith("/files/replicated"):
                self._proxy(f"http://{rest[:-len('/files/replicated')]}:{NODE_PORT}/node/files/replicated")
            elif rest.endswith("/info"):
                self._proxy(f"http://{rest[:-len('/info')]}:{NODE_PORT}/node/info")
            else:
                self.send_response(404); self.end_headers()
        else:
            self.send_response(404); self.end_headers()

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length) if length else b"{}"
        try:
            data = json.loads(body)
        except Exception:
            data = {}

        path = urlparse(self.path).path
        if path == "/api/node/add":
            self._add_node(data)
        elif path == "/api/node/remove":
            self._remove_node(data)
        elif path == "/api/node/restart":
            self._restart_node(data)
        elif path.startswith("/api/node/") and path.endswith("/lock"):
            ip = path[len("/api/node/"):-len("/lock")]
            self._node_lock_action(ip, data.get("filename", ""), "lock")
        elif path.startswith("/api/node/") and path.endswith("/unlock"):
            ip = path[len("/api/node/"):-len("/unlock")]
            self._node_lock_action(ip, data.get("filename", ""), "unlock")
        else:
            self.send_response(404); self.end_headers()

    def _proxy(self, url):
        data = http_get(url)
        if data:
            self.send_text(200, data)
        else:
            self.send_json(503, {"error": "unreachable"})

    def _nodes(self):
        self.send_json(200, build_node_list())

    def _containers(self):
        try:
            r = subprocess.run(
                ["docker", "ps", "-a", "--format",
                 "{{.Names}}\t{{.Status}}\t{{.Image}}"],
                capture_output=True, text=True, timeout=5
            )
            containers = []
            for line in r.stdout.strip().splitlines():
                parts = line.split("\t")
                containers.append({
                    "name": parts[0] if len(parts) > 0 else "",
                    "status": parts[1] if len(parts) > 1 else "",
                    "image": parts[2] if len(parts) > 2 else "",
                })
            self.send_json(200, containers)
        except Exception as e:
            self.send_json(500, {"error": str(e)})

    def _logs(self, container, lines):
        try:
            r = subprocess.run(
                ["docker", "logs", container, "--tail", str(lines)],
                capture_output=True, text=True, timeout=10
            )
            self.send_text(200, r.stdout + r.stderr)
        except Exception as e:
            self.send_text(500, str(e))

    def _add_node(self, data):
        name = data.get("name", "").strip()
        ip = data.get("ip", "").strip()
        container = data.get("containerName", "").strip()
        if not name or not ip or not container:
            self.send_json(400, {"error": "name, ip, containerName zijn verplicht"})
            return

        data_path = f"/data/{container}"
        image = get_node_image()
        network = get_node_network()

        cmd = [
            "docker", "run", "-d",
            "--name", container,
            f"--network={network}",
            f"--ip={ip}",
            "-e", "NAMING_SERVER_URL=http://naming-server:8080",
            "-v", f"{container}-data:{data_path}",
            "--entrypoint", "sh",
            image,
            "-c",
            f"sleep 5 && exec java -Djava.net.preferIPv4Stack=true "
            f"-Dloader.main=discovery.ciscos.distlab4.NodeApplication "
            f"-jar /app/app.jar {name} {ip} {data_path}"
        ]

        try:
            r = subprocess.run(cmd, capture_output=True, text=True, timeout=15)
            if r.returncode == 0:
                self.send_json(200, {"ok": True, "containerId": r.stdout.strip()[:12]})
            else:
                self.send_json(500, {"error": r.stderr.strip()})
        except Exception as e:
            self.send_json(500, {"error": str(e)})

    def _remove_node(self, data):
        container = data.get("containerName", "").strip()
        if not container:
            self.send_json(400, {"error": "containerName is verplicht"})
            return
        try:
            r = subprocess.run(
                ["docker", "stop", container],
                capture_output=True, text=True, timeout=30
            )
            if r.returncode == 0:
                self.send_json(200, {"ok": True})
            else:
                self.send_json(500, {"error": r.stderr.strip()})
        except Exception as e:
            self.send_json(500, {"error": str(e)})

    def _node_lock_action(self, ip, filename, action):
        if not filename:
            self.send_json(400, {"error": "filename required"})
            return
        import urllib.parse
        encoded = urllib.parse.quote(filename)
        url = f"http://{ip}:{NODE_PORT}/node/{action}?filename={encoded}"
        try:
            req = urllib.request.Request(url, data=b"", method="POST")
            with urllib.request.urlopen(req, timeout=3):
                pass
            self.send_json(200, {"ok": True})
        except Exception as e:
            self.send_json(500, {"error": str(e)})

    def _restart_node(self, data):
        container = data.get("containerName", "").strip()
        if not container:
            self.send_json(400, {"error": "containerName is verplicht"})
            return
        try:
            r = subprocess.run(
                ["docker", "start", container],
                capture_output=True, text=True, timeout=30
            )
            if r.returncode == 0:
                self.send_json(200, {"ok": True})
            else:
                self.send_json(500, {"error": r.stderr.strip()})
        except Exception as e:
            self.send_json(500, {"error": str(e)})


if __name__ == "__main__":
    print(f"[GUI] http://localhost:{SERVE_PORT}")
    http.server.HTTPServer(("0.0.0.0", SERVE_PORT), Handler).serve_forever()
