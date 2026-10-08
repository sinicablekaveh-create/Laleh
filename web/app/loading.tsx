export default function Loading() {
  return <main aria-busy="true">
    <header><span className="brand">لاله</span></header>
    <section className="hero loading-panel" role="status" aria-live="polite">
      <p className="eyebrow">در حال بارگذاری</p>
      <h1>در حال آماده‌سازی فهرست…</h1>
      <div className="loading-lines" aria-hidden="true"><span /><span /><span /></div>
      <p>اطلاعات عمومی در حال آماده‌شدن است.</p>
    </section>
  </main>;
}
