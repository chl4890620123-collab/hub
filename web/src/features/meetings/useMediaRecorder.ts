import { useRef, useState } from 'react';

const MAX_DURATION_MS = 25 * 60 * 1000;

export function useMediaRecorder() {
  const [isRecording, setIsRecording] = useState(false);
  const [elapsedMs, setElapsedMs] = useState(0);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const streamRef = useRef<MediaStream | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const resolveRef = useRef<((blob: Blob) => void) | null>(null);

  async function start() {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    streamRef.current = stream;
    chunksRef.current = [];
    const recorder = new MediaRecorder(stream);
    recorder.ondataavailable = (e) => {
      if (e.data.size > 0) chunksRef.current.push(e.data);
    };
    recorder.onstop = () => {
      const blob = new Blob(chunksRef.current, { type: 'audio/webm' });
      resolveRef.current?.(blob);
      resolveRef.current = null;
    };
    recorder.start();
    recorderRef.current = recorder;
    setIsRecording(true);
    setElapsedMs(0);

    const startedAt = Date.now();
    timerRef.current = setInterval(() => {
      const elapsed = Date.now() - startedAt;
      setElapsedMs(elapsed);
      if (elapsed >= MAX_DURATION_MS) stop();
    }, 500);
  }

  function stop(): Promise<Blob> {
    return new Promise((resolve) => {
      resolveRef.current = resolve;
      recorderRef.current?.stop();
      streamRef.current?.getTracks().forEach((track) => track.stop());
      if (timerRef.current) clearInterval(timerRef.current);
      setIsRecording(false);
    });
  }

  return { isRecording, elapsedMs, start, stop, maxDurationMs: MAX_DURATION_MS };
}
