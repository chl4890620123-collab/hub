import { useRef, useState } from 'react';

const MAX_DURATION_MS = 25 * 60 * 1000;

/** Prefers opus-in-webm, falls back through what the browser actually supports - Safari/iOS in
 * particular don't support webm at all, so a hardcoded mimeType silently produces broken audio. */
function chooseMime(): string {
  const candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4'];
  return candidates.find((type) => window.MediaRecorder?.isTypeSupported?.(type)) ?? '';
}

function recordingErrorMessage(error: unknown): string {
  if (!window.isSecureContext) {
    return '브라우저 직접 녹음은 localhost 또는 HTTPS에서 사용할 수 있습니다. 음성 파일 업로드를 이용해 주세요.';
  }
  if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) {
    return '이 브라우저는 직접 녹음을 지원하지 않습니다. 음성 파일을 선택해 주세요.';
  }
  const name = error instanceof DOMException ? error.name : undefined;
  if (name === 'NotAllowedError') return '마이크 권한이 차단되었습니다. 브라우저 주소창의 마이크 권한을 허용해 주세요.';
  if (name === 'NotFoundError') return '연결된 마이크를 찾지 못했습니다. 음성 파일 업로드를 이용해 주세요.';
  const detail = error instanceof Error ? error.message : undefined;
  return `녹음을 시작하지 못했습니다.${detail ? ` ${detail}` : ' 음성 파일 업로드를 이용해 주세요.'}`;
}

export function useMediaRecorder(onAutoStop?: (blob: Blob) => void) {
  const [isRecording, setIsRecording] = useState(false);
  const [elapsedMs, setElapsedMs] = useState(0);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const streamRef = useRef<MediaStream | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const resolveRef = useRef<((blob: Blob) => void) | null>(null);
  const mimeRef = useRef<string>('audio/webm');

  async function start() {
    if (!window.isSecureContext) throw new Error(recordingErrorMessage(null));
    if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) throw new Error(recordingErrorMessage(null));
    let stream: MediaStream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true },
      });
    } catch (error) {
      throw new Error(recordingErrorMessage(error));
    }
    if (!stream.getAudioTracks().some((track) => track.readyState === 'live')) {
      stream.getTracks().forEach((track) => track.stop());
      throw new Error('사용 가능한 마이크 입력이 없습니다.');
    }
    streamRef.current = stream;
    chunksRef.current = [];
    const mime = chooseMime();
    mimeRef.current = mime || 'audio/webm';
    const recorder = new MediaRecorder(stream, mime ? { mimeType: mime } : undefined);
    recorder.ondataavailable = (e) => {
      if (e.data.size > 0) chunksRef.current.push(e.data);
    };
    recorder.onstop = () => {
      const blob = new Blob(chunksRef.current, { type: recorder.mimeType || mimeRef.current });
      resolveRef.current?.(blob);
      resolveRef.current = null;
    };
    recorder.start(1000);
    recorderRef.current = recorder;
    setIsRecording(true);
    setElapsedMs(0);

    const startedAt = Date.now();
    timerRef.current = setInterval(() => {
      const elapsed = Date.now() - startedAt;
      setElapsedMs(elapsed);
      if (elapsed >= MAX_DURATION_MS) {
        stop().then((blob) => onAutoStop?.(blob));
      }
    }, 500);
  }

  function stop(): Promise<Blob> {
    return new Promise((resolve) => {
      const recorder = recorderRef.current;
      if (!recorder || recorder.state === 'inactive') {
        resolve(new Blob(chunksRef.current, { type: recorder?.mimeType || mimeRef.current }));
        return;
      }
      resolveRef.current = resolve;
      recorder.stop();
      streamRef.current?.getTracks().forEach((track) => track.stop());
      if (timerRef.current) clearInterval(timerRef.current);
      setIsRecording(false);
    });
  }

  return { isRecording, elapsedMs, start, stop, maxDurationMs: MAX_DURATION_MS };
}
