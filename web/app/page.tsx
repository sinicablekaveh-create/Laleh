import { searchQuery } from "../lib/query";
import { localCatalog } from "../lib/local-catalog";
import Link from "next/link";

export default async function DiscoveryPage({ searchParams }: {
  searchParams: Promise<{ q?: string | string[]; page?: string }>;
}) {
  const params = await searchParams;
  const query = searchQuery(params.q);
  const page = Math.max(1, Math.min(500, Number.parseInt(params.page ?? "1", 10) || 1));
  const result = localCatalog.search(query, (page - 1) * 20, 20);
  const pageLink = (value: number) => `/?${new URLSearchParams({ q: query, page: String(value) })}`;
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
    <section aria-live="polite" aria-label="نتایج جست‌وجو">
      <h2>{query ? `نتایج برای «${query}»` : "فهرست گروه‌های عمومی"}</h2>
      <p>{new Intl.NumberFormat("fa-IR").format(result.total)} گروه</p>
      {result.hits.length === 0 ? <div className="empty"><span aria-hidden="true">✳</span>
        <p>{result.total ? "در این صفحه نتیجه‌ای نیست؛ صفحهٔ قبل را انتخاب کن."
          : "گروه عمومی تأییدشده‌ای برای نمایش پیدا نشد."}</p></div>
        : <div className="results">{result.hits.map(({ group }) => <article className="group" key={group.groupId}>
          <h3>{group.title}</h3><p>{[group.category, group.location].filter(Boolean).join(" · ")}</p>
          <a href={`https://t.me/${group.username}`} target="_blank" rel="noopener noreferrer">مشاهده در تلگرام ↗</a>
        </article>)}</div>}
      <nav className="pages" aria-label="صفحه‌های نتیجه">
        {page > 1 && <Link href={pageLink(page - 1)}>صفحهٔ قبل</Link>}
        {page * 20 < result.total && <Link href={pageLink(page + 1)}>صفحهٔ بعد</Link>}
      </nav>
    </section>
    <footer id="about"><h2>جست‌وجو بر اساس موضوع و شهر</h2>
      <p>این فهرست برای اطلاعات عمومی گروه‌هاست. ورود به تلگرام و مدیریت حساب در اپ اندروید لاله انجام می‌شود.</p>
    </footer>
  </main>;
}
