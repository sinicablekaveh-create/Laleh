import { searchQuery } from "../lib/query";

export default async function DiscoveryPage({ searchParams }: {
  searchParams: Promise<{ q?: string | string[] }>;
}) {
  const query = searchQuery((await searchParams).q);
  return <main>
    <header><a className="brand" href="/">لاله<span>کشف گروه‌های عمومی</span></a>
      <a href="#about">دربارهٔ جست‌وجو</a></header>
    <section className="hero">
      <p className="eyebrow">برق · صنعت · ارتباط</p>
      <h1>گروه مرتبط را<br />از همین‌جا پیدا کن.</h1>
      <p className="intro">موضوع یا شهر را وارد کن؛ مثل «برق صنعتی تهران».</p>
      <form action="/" method="get" role="search">
        <label htmlFor="query">موضوع، واژه یا شهر</label>
        <div className="search"><input id="query" name="q" maxLength={96}
          defaultValue={query} placeholder="برق صنعتی تهران" type="search" />
          <button type="submit">جست‌وجو</button></div>
      </form>
    </section>
    <section className="empty" aria-live="polite">
      <span aria-hidden="true">✳</span>
      <h2>{query ? `نتایج برای «${query}»` : "فهرست گروه‌های عمومی"}</h2>
      <p>هنوز گروه عمومی تأییدشده‌ای در این فهرست ثبت نشده است.</p>
    </section>
    <footer id="about"><h2>جست‌وجو بر اساس موضوع و شهر</h2>
      <p>این فهرست برای اطلاعات عمومی گروه‌هاست. ورود به تلگرام و مدیریت حساب در اپ اندروید لاله انجام می‌شود.</p>
    </footer>
  </main>;
}
