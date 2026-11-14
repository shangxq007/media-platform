import { Storage } from 'happy-dom'

// Node >=25 exposes a global `localStorage` that is `undefined` unless
// --localstorage-file is provided; that global shadows happy-dom's
// window.localStorage and breaks every suite that reads the bare global.
// Restore a Storage implementation when the environment did not provide one.
// sessionStorage is unaffected because Node does not define a competing global.
if (typeof globalThis.localStorage === 'undefined') {
  Object.defineProperty(globalThis, 'localStorage', {
    value: new Storage(),
    configurable: true,
    writable: true,
  })
}
