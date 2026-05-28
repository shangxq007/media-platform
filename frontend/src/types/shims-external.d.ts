declare module '@openreplay/tracker' {
  const Tracker: {
    new (opts: Record<string, unknown>): Record<string, unknown>
  }
  export default Tracker
}
