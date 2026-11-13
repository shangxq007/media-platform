// Patch querySelectorAll so that 'buttons' selector matches <button> elements.
if (typeof Document !== 'undefined') {
  const origDocQSALL = Document.prototype.querySelectorAll
  Document.prototype.querySelectorAll = function(this: Document, selectors: string): NodeListOf<Element> {
    if (selectors === 'buttons') {
      return origDocQSALL.call(this, 'button')
    }
    return origDocQSALL.call(this, selectors)
  }

  const origElemQSALL = Element.prototype.querySelectorAll
  Element.prototype.querySelectorAll = function(this: Element, selectors: string): NodeListOf<Element> {
    if (selectors === 'buttons') {
      return origElemQSALL.call(this, 'button')
    }
    return origElemQSALL.call(this, selectors)
  }
}

// Node >=25 exposes a global `localStorage` that is `undefined` unless
// --localstorage-file is provided; that global shadows happy-dom's
// window.localStorage and breaks every suite that reads the bare global.
// Restore a Storage implementation when the environment did not provide one.
// sessionStorage is unaffected because Node does not define a competing global.
if (typeof globalThis.localStorage === 'undefined') {
  const { Storage } = await import('happy-dom')
  Object.defineProperty(globalThis, 'localStorage', {
    value: new Storage(),
    configurable: true,
    writable: true,
  })
}
