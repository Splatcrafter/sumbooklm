/**
 * The services a model can be requested from, in the order they are offered.
 */
export const AI_PROVIDERS = ['OPENAI', 'GROQ', 'OLLAMA'] as const;

/**
 * Service a model is requested from.
 */
export type AiProvider = (typeof AI_PROVIDERS)[number];

/**
 * Everything the browser keeps about how questions are answered.
 *
 * The key is part of this and is therefore never sent anywhere except to the backend of this
 * application, which forwards it to the selected provider and keeps nothing.
 */
export interface ModelSettings {
  provider: AiProvider;
  model: string;
  apiKey: string;
  baseUrl: string;
}

/**
 * The state a visitor starts in, which is one where nothing can be asked yet.
 *
 * The provider is preselected because a choice has to start somewhere, while the model is not: a
 * guessed model name would be a setting that looks configured and fails on the first question.
 */
export const EMPTY_MODEL_SETTINGS: ModelSettings = {
  provider: 'OPENAI',
  model: '',
  apiKey: '',
  baseUrl: '',
};

/**
 * Examples shown in the empty fields of the settings, per provider.
 *
 * The addresses repeat what the backend falls back to when none is given. They are placeholders and
 * are never sent, so a backend that changes its default makes a hint stale rather than a request
 * wrong.
 */
export const PROVIDER_HINTS: Record<AiProvider, { model: string; baseUrl: string }> = {
  OPENAI: { model: 'gpt-4o-mini', baseUrl: 'https://api.openai.com/v1' },
  GROQ: { model: 'llama-3.3-70b-versatile', baseUrl: 'https://api.groq.com/openai/v1' },
  OLLAMA: { model: 'llama3.2', baseUrl: 'http://localhost:11434' },
};

/**
 * Reports whether a provider has to be addressed with a key.
 *
 * A locally running server needs none: reaching it already means being on the machine it runs on.
 */
export function requiresApiKey(provider: AiProvider): boolean {
  return provider !== 'OLLAMA';
}

/**
 * Reports whether questions can be asked with these settings.
 *
 * The same rules are enforced by the backend, which rejects a request that is missing one of them.
 * Checking here as well is what lets the interface say so before a question is sent.
 */
export function isConfigured(settings: ModelSettings): boolean {
  if (settings.model.trim() === '') {
    return false;
  }
  return !requiresApiKey(settings.provider) || settings.apiKey.trim() !== '';
}

/**
 * Builds the headers a question carries its model access in.
 *
 * Empty values are left out rather than sent as empty strings, so that the backend falls back to the
 * default address of the provider instead of being handed one that is not an address.
 */
export function modelHeaders(settings: ModelSettings): Record<string, string> {
  const headers: Record<string, string> = {
    'X-AI-Provider': settings.provider,
    'X-AI-Model': settings.model.trim(),
  };
  if (settings.apiKey.trim() !== '') {
    headers['X-AI-Api-Key'] = settings.apiKey.trim();
  }
  if (settings.baseUrl.trim() !== '') {
    headers['X-AI-Base-Url'] = settings.baseUrl.trim();
  }
  return headers;
}

/**
 * Narrows a stored value back into settings, or returns null when it is not one.
 *
 * The stored form is written by an earlier version of this application, so it is treated like any
 * other input rather than trusted because it came from the same origin.
 */
export function toModelSettings(value: unknown): ModelSettings | null {
  if (typeof value !== 'object' || value === null) {
    return null;
  }
  const candidate = value as Record<string, unknown>;
  const provider = AI_PROVIDERS.find((known) => known === candidate.provider);
  if (!provider) {
    return null;
  }
  return {
    provider,
    model: typeof candidate.model === 'string' ? candidate.model : '',
    apiKey: typeof candidate.apiKey === 'string' ? candidate.apiKey : '',
    baseUrl: typeof candidate.baseUrl === 'string' ? candidate.baseUrl : '',
  };
}
