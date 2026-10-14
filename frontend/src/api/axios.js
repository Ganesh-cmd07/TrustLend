import axios from "axios";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || import.meta.env.VITE_API_URL || "http://localhost:4040"
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem("trustlend_jwt")
    || sessionStorage.getItem("trustlend_jwt");

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

export function getApiErrorMessage(error, fallback) {
  const data = error.response?.data;
  if (data?.details?.fields && typeof data.details.fields === "object") {
    const fieldMessages = Object.values(data.details.fields).filter(Boolean);
    if (fieldMessages.length > 0) {
      return fieldMessages.join(". ");
    }
  }
  return data?.message
    || (error.response
      ? `Request failed (${error.response.status})`
      : fallback);
}

export async function apiFetch(url, options = {}) {
  try {
    const response = await api.request({
      url,
      method: options.method || "GET",
      headers: options.headers,
      data: options.body,
      signal: options.signal,
      validateStatus: () => true
    });

    return {
      status: response.status,
      ok: response.status >= 200 && response.status < 300,
      json: async () => response.data
    };
  } catch (error) {
    if (error.code === "ERR_CANCELED") {
      const abortError = new Error("The operation was aborted");
      abortError.name = "AbortError";
      throw abortError;
    }
    throw error;
  }
}

export default api;
