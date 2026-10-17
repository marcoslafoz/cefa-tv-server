type Listener = () => void

const unauthorizedListeners = new Set<Listener>()
const forbiddenListeners = new Set<Listener>()

function subscribe(listeners: Set<Listener>, listener: Listener) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export const authEvents = {
  onUnauthorized: (listener: Listener) => subscribe(unauthorizedListeners, listener),
  onForbidden: (listener: Listener) => subscribe(forbiddenListeners, listener),
  emit401() {
    unauthorizedListeners.forEach((listener) => listener())
  },
  emitForbidden() {
    forbiddenListeners.forEach((listener) => listener())
  },
}
