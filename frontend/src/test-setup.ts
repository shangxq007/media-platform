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
