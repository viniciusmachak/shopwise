import { useCallback, useEffect, useRef, useState } from 'react'

interface RecognitionResult {
  isFinal: boolean
  0: { transcript: string }
}

interface RecognitionEvent {
  resultIndex: number
  results: ArrayLike<RecognitionResult>
}

interface Recognition {
  lang: string
  interimResults: boolean
  continuous: boolean
  onresult: ((event: RecognitionEvent) => void) | null
  onend: (() => void) | null
  onerror: (() => void) | null
  start(): void
  stop(): void
}

type RecognitionConstructor = new () => Recognition

function recognitionConstructor(): RecognitionConstructor | undefined {
  const w = window as unknown as {
    SpeechRecognition?: RecognitionConstructor
    webkitSpeechRecognition?: RecognitionConstructor
  }
  return w.SpeechRecognition ?? w.webkitSpeechRecognition
}

export function isVoiceSupported(): boolean {
  return typeof window !== 'undefined' && !!recognitionConstructor() && 'speechSynthesis' in window
}

interface UseVoiceOptions {
  onInterim: (text: string) => void
  onFinal: (text: string) => void
}

/**
 * Speech in and out through the browser's Web Speech API (en-US).
 * Starting to listen cancels any speech in progress (barge-in).
 */
export function useVoice({ onInterim, onFinal }: UseVoiceOptions) {
  const [listening, setListening] = useState(false)
  const recognition = useRef<Recognition | null>(null)
  const callbacks = useRef({ onInterim, onFinal })

  useEffect(() => {
    callbacks.current = { onInterim, onFinal }
  }, [onInterim, onFinal])

  const supported = isVoiceSupported()

  const start = useCallback(() => {
    const Ctor = recognitionConstructor()
    if (!Ctor) return
    window.speechSynthesis.cancel()
    const r = new Ctor()
    r.lang = 'en-US'
    r.interimResults = true
    r.continuous = false
    r.onresult = (event) => {
      let interim = ''
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i]
        if (!result) continue
        if (result.isFinal) callbacks.current.onFinal(result[0].transcript.trim())
        else interim += result[0].transcript
      }
      if (interim) callbacks.current.onInterim(interim)
    }
    r.onend = () => setListening(false)
    r.onerror = () => setListening(false)
    recognition.current = r
    r.start()
    setListening(true)
  }, [])

  const stop = useCallback(() => {
    recognition.current?.stop()
    setListening(false)
  }, [])

  const speak = useCallback((text: string) => {
    if (!supported || !text.trim()) return
    const utterance = new SpeechSynthesisUtterance(text)
    utterance.lang = 'en-US'
    window.speechSynthesis.speak(utterance)
  }, [supported])

  const cancelSpeech = useCallback(() => {
    if (supported) window.speechSynthesis.cancel()
  }, [supported])

  return { supported, listening, start, stop, speak, cancelSpeech }
}

/**
 * Splits streamed text into complete sentences, keeping the unfinished rest.
 * A sentence ends at . ! or ? followed by whitespace, so "$89.90" is not split.
 */
export function takeSentences(buffer: string): { sentences: string[]; rest: string } {
  const sentences: string[] = []
  const boundary = /[.!?]+\s+/g
  let start = 0
  let match: RegExpExecArray | null
  while ((match = boundary.exec(buffer)) !== null) {
    const end = match.index + match[0].length
    const sentence = buffer.slice(start, end).trim()
    if (sentence) sentences.push(sentence)
    start = end
  }
  return { sentences, rest: buffer.slice(start) }
}
