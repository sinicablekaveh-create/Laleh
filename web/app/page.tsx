import { searchState, searchLink, type SearchParameters } from "../lib/search-options";
import { normalizeQuery } from "../lib/query";
import { localCatalog } from "../lib/local-catalog";
import Link from "next/link";
import { GroupCards } from "./components/group-cards";

export default async function DiscoveryPage({ searchParams }: {
  searchParams: Promise<SearchParameters>;
}) {
  const params = await searchParams;
  const state = searchState(params);
  const { query, page } = state;
  const result = localCatalog.search(query, (page - 1) * 20, 20, state);
  const categories = localCatalog.categories();
  const locations = localCatalog.locations();
  const pageLink = (value: number) => searchLink(state, value);
  return <main>
    <a className="skip-link" href="#search-results">رفتن به نتایج جست‌وجو</a>
    <header><a className="brand" href="/">لاله<span>کشف گروه‌های عمومی</span></a>
      <Link href="/categories">دسته‌بندی‌ها</Link></header>
    <section className="hero">
      <p className="eyebrow">برق · صنعت · ارتباط</p>
      <h1>گروه مرتبط را<br />از همین‌جا پیدا کن.</h1>
      <p className="intro">موضوع یا شهر را وارد کن؛ مثل «برق صنعتی تهران».</p>
      <form action="/" method="get" role="search">
        <label htmlFor="query">موضوع، واژه یا شهر</label>
        <div className="search"><input id="query" name="q" maxLength={96}
          defaultValue={query} placeholder="برق صنعتی تهران" type="search" />
          <button type="submit">جست‌وجو</button></div>
        <fieldset className="filters"><legend>فیلتر و ترتیب نتایج</legend>
          <div><label htmlFor="category">دسته‌بندی</label>
            <select id="category" name="category" defaultValue={state.category}>
              <option value="">همهٔ دسته‌بندی‌ها</option>
              {state.category && !categories.some(row => normalizeQuery(row.name) === state.category)
                && <option value={state.category}>{state.category}</option>}
              {categories.map(row => <option key={row.name} value={normalizeQuery(row.name)}>{row.name}</option>)}
            </select></div>
          <div><label htmlFor="location">شهر یا منطقه</label>
            <select id="location" name="location" defaultValue={state.location}>
              <option value="">همهٔ شهرها</option>
              {state.location && !locations.some(name => normalizeQuery(name) === state.location)
                && <option value={state.location}>{state.location}</option>}
              {locations.map(name => <option key={name} value={normalizeQuery(name)}>{name}</option>)}
            </select></div>
          <div><label htmlFor="sort">مرتب‌سازی</label>
            <select id="sort" name="sort" defaultValue={state.sort}>
              <option value="relevance">مرتبط‌ترین</option><option value="title">عنوان گروه</option>
            </select></div>
        </fieldset>
        <p><Link href="/">پاک کردن جست‌وجو و فیلترها</Link></p>
      </form>
    </section>
    <section id="search-results" tabIndex={-1} aria-live="polite" aria-label="نتایج جست‌وجو">
      <h2>{query ? `نتایج برای «${query}»` : "فهرست گروه‌های عمومی"}</h2>
      <p>{new Intl.NumberFormat("fa-IR").format(result.total)} گروه</p>
      {result.hits.length === 0 ? <div className="empty"><span aria-hidden="true">✳</span>
        <p>{result.total ? "در این صفحه نتیجه‌ای نیست؛ صفحهٔ قبل را انتخاب کن."
          : "گروه عمومی تأییدشده‌ای برای نمایش پیدا نشد."}</p></div>
        : <GroupCards hits={result.hits} />}
      <nav className="pages" aria-label="صفحه‌های نتیجه">
        <span aria-current="page">صفحهٔ {page.toLocaleString("fa-IR")}</span>
        {page > 1 && <Link href={pageLink(page - 1)}>صفحهٔ قبل</Link>}
        {page * 20 < result.total && <Link href={pageLink(page + 1)}>صفحهٔ بعد</Link>}
      </nav>
    </section>
    <footer id="about"><h2>جست‌وجو بر اساس موضوع و شهر</h2>
      <p>این فهرست برای اطلاعات عمومی گروه‌هاست. ورود به تلگرام و مدیریت حساب در اپ اندروید لاله انجام می‌شود.</p>
    </footer>
  </main>;
}
