#!/usr/bin/env python3
"""
OpenClaw Assistant – Mock Backend Server
=========================================
Bridges the Android app to your real OpenClaw agent OR runs in mock mode.

Usage:
  pip install fastapi uvicorn python-dotenv
  python mock_server.py

Environment variables (.env file or export):
  OPENCLAW_MODE     = mock | anthropic | openai | proxy   (default: mock)
  OPENCLAW_API_KEY  = your_api_key_here
  OPENCLAW_AGENT_URL= ws://your-real-agent:port/ws        (proxy mode)
  OPENCLAW_HOST     = 0.0.0.0
  OPENCLAW_PORT     = 8000
  JWT_SECRET        = your_jwt_secret                     (skip check if blank)
"""

import asyncio
import base64
import json
import logging
import os
import time
from typing import Optional

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException
from fastapi.middleware.cors import CORSMiddleware

# ── Optional imports ─────────────────────────────────────
try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

try:
    import anthropic as _anthropic
    ANTHROPIC_AVAILABLE = True
except ImportError:
    ANTHROPIC_AVAILABLE = False

# ── Config ────────────────────────────────────────────────
MODE          = os.getenv("OPENCLAW_MODE", "mock")
API_KEY       = os.getenv("OPENCLAW_API_KEY", "")
AGENT_URL     = os.getenv("OPENCLAW_AGENT_URL", "")
HOST          = os.getenv("OPENCLAW_HOST", "0.0.0.0")
PORT          = int(os.getenv("OPENCLAW_PORT", "8000"))
JWT_SECRET    = os.getenv("JWT_SECRET", "")

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("openclaw")

app = FastAPI(title="OpenClaw Mock Backend", version="2.0.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

# ── Connection tracker ────────────────────────────────────
class ConnectionManager:
    def __init__(self):
        self.active: dict[str, WebSocket] = {}
        self.stats: dict[str, dict] = {}

    async def connect(self, ws: WebSocket, cid: str):
        await ws.accept()
        self.active[cid] = ws
        self.stats[cid] = {"connected_at": time.time(), "audio_chunks": 0, "frames": 0, "responses": 0}
        log.info(f"Client connected: {cid} | total={len(self.active)}")

    def disconnect(self, cid: str):
        self.active.pop(cid, None)
        log.info(f"Client disconnected: {cid}")

    async def send(self, ws: WebSocket, payload: dict):
        try:
            await ws.send_text(json.dumps(payload))
        except Exception as e:
            log.warning(f"Send error: {e}")

mgr = ConnectionManager()

# ── AI backends ──────────────────────────────────────────
async def handle_audio_mock(audio_bytes: bytes) -> dict:
    await asyncio.sleep(0.2)  # Simulate processing
    return {
        "type": "assistant_response",
        "text": f"OpenClaw heard you! ({len(audio_bytes)} bytes of audio received)",
        "audio_base64": None,
        "emotion": "happy"
    }

async def handle_vision_mock(base64_frame: str) -> dict:
    return {
        "type": "assistant_response",
        "text": f"I see a camera frame ({len(base64_frame)} base64 chars).",
        "audio_base64": None,
        "emotion": "neutral"
    }

async def handle_audio_anthropic(audio_bytes: bytes) -> dict:
    if not ANTHROPIC_AVAILABLE:
        return await handle_audio_mock(audio_bytes)
    # Anthropic does not yet support audio input natively –
    # transcribe first (e.g. Whisper), then send text to Claude
    return {
        "type": "assistant_response",
        "text": "Anthropic mode: audio received. Integrate Whisper for transcription.",
        "audio_base64": None,
        "emotion": "thinking"
    }

async def handle_text_anthropic(text: str) -> dict:
    if not ANTHROPIC_AVAILABLE or not API_KEY:
        return {"type": "assistant_response", "text": f"Echo: {text}", "emotion": "neutral"}
    try:
        client = _anthropic.Anthropic(api_key=API_KEY)
        message = client.messages.create(
            model="claude-opus-4-6",
            max_tokens=256,
            system="You are OpenClaw, a helpful AI assistant running on Android.",
            messages=[{"role": "user", "content": text}]
        )
        return {
            "type": "assistant_response",
            "text": message.content[0].text,
            "audio_base64": None,
            "emotion": "happy"
        }
    except Exception as e:
        log.error(f"Anthropic error: {e}")
        return {"type": "error", "text": str(e)}

# ── WebSocket endpoint ────────────────────────────────────
@app.websocket("/ws")
async def websocket_endpoint(ws: WebSocket):
    cid = f"{ws.client.host}:{ws.client.port}"
    await mgr.connect(ws, cid)
    stats = mgr.stats[cid]

    # Send welcome
    await mgr.send(ws, {
        "type": "connected",
        "text": f"OpenClaw backend ready (mode={MODE})",
        "emotion": "happy"
    })

    try:
        while True:
            msg = await ws.receive()

            if "bytes" in msg and msg["bytes"]:
                # Binary frame = Opus/PCM audio chunk
                audio = msg["bytes"]
                stats["audio_chunks"] += 1

                if MODE == "anthropic":
                    response = await handle_audio_anthropic(audio)
                else:
                    response = await handle_audio_mock(audio)

                stats["responses"] += 1
                await mgr.send(ws, response)

            elif "text" in msg and msg["text"]:
                try:
                    data = json.loads(msg["text"])
                except json.JSONDecodeError:
                    continue

                msg_type = data.get("type", "")

                if msg_type == "vision_frame":
                    stats["frames"] += 1
                    if stats["frames"] % 5 == 1:  # Ack every 5th frame
                        response = await handle_vision_mock(data.get("data", ""))
                        await mgr.send(ws, response)

                elif msg_type == "audio_meta":
                    log.info(f"Audio format: {data}")
                    await mgr.send(ws, {"type": "ack", "text": "Audio format received"})

                elif msg_type == "text_input":
                    if MODE == "anthropic":
                        response = await handle_text_anthropic(data.get("text", ""))
                    else:
                        response = await handle_audio_mock(b"")
                    await mgr.send(ws, response)

    except WebSocketDisconnect:
        pass
    except Exception as e:
        log.error(f"WS error for {cid}: {e}")
    finally:
        mgr.disconnect(cid)

# ── Health / admin endpoints ──────────────────────────────
@app.get("/health")
def health():
    return {"status": "ok", "mode": MODE, "connections": len(mgr.active)}

@app.get("/stats")
def stats():
    return {
        "connections": len(mgr.active),
        "clients": {
            cid: {**s, "uptime_s": round(time.time() - s["connected_at"])}
            for cid, s in mgr.stats.items()
        }
    }

@app.get("/")
def index():
    return {
        "name": "OpenClaw Backend",
        "version": "2.0.0",
        "endpoints": {
            "ws": f"ws://{HOST}:{PORT}/ws",
            "health": f"http://{HOST}:{PORT}/health",
            "stats": f"http://{HOST}:{PORT}/stats"
        }
    }

# ── Entry point ───────────────────────────────────────────
if __name__ == "__main__":
    import uvicorn
    log.info(f"Starting OpenClaw backend on {HOST}:{PORT} [mode={MODE}]")
    uvicorn.run(app, host=HOST, port=PORT, log_level="info")
