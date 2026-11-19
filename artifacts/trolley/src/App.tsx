import { useEffect, useMemo, useState } from 'react';
import { ArrowRight, ArrowUpRight, Search, MapPin, SlidersHorizontal, Menu, X, Sun, Moon, Heart, ShieldCheck, Wrench, Zap, Sparkles, Car, GraduationCap, BriefcaseBusiness, Scissors, Camera, ChevronDown, Check, LockKeyhole, UserRound, Store, MessageCircle, BadgeCheck, Clock3, Plus, CircleHelp, ShoppingBasket, Star, Mail, Eye, EyeOff } from 'lucide-react';
import { categories, categoryFamilies, subcategoryCount } from './lib/categories';
import { authRedirectUrl, isSupabaseConfigured, supabase } from './lib/supabase';
import { useAccount } from './lib/useAccount';
import { useProviderProfile } from './lib/useProviderProfile';
import { PasswordRecovery } from './components/PasswordRecovery';
import { ProviderEditor } from './components/ProviderEditor';
import { SearchSelect } from './components/SearchSelect';
import { RegionCountryFields } from './components/LocationFields';
import { countries, getCountryLabel, getRegionForCountry, regions } from './lib/geography';

type Modal = 'signin' | 'signup' | 'forgot' | 'provider' | 'privacy' | 'terms' | null;
const familyIcons = [Wrench, Zap, Sparkles, Car, BriefcaseBusiness, GraduationCap, Scissors, Camera];
const familyHighlights = [
  { name: 'Home & repairs', icon: Wrench, detail: 'Plumbing, electrical, roofing and more' },
  { name: 'Business services', icon: BriefcaseBusiness, detail: 'Accounting, legal, IT and marketing' },
  { name: 'Beauty & wellness', icon: Scissors, detail: 'Hair, beauty, fitness and personal care' },
  { name: 'Events & creative', icon: Camera, detail: 'Photography, catering and event planning' },
  { name: 'Transport & auto', icon: Car, detail: 'Mechanics, moving and delivery' },
  { name: 'Learning & care', icon: GraduationCap, detail: 'Tutors, childcare and elder care' },
];

function App() {
  const [dark, setDark] = useState(() => localStorage.getItem('trolley-theme') === 'dark');
  const [query, setQuery] = useState('');
  const [location, setLocation] = useState(() => localStorage.getItem('trolley-discovery-country') ?? 'ZW');
  const [activeCategory, setActiveCategory] = useState('All services');
  const [modal, setModal] = useState<Modal>(null);
  const [mobileMenu, setMobileMenu] = useState(false);
  const [cookieChoice, setCookieChoice] = useState<string | null>(() => localStorage.getItem('trolley-consent'));
  const [showAll, setShowAll] = useState(false);
  const [age, setAge] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [providerBusiness, setProviderBusiness] = useState('');
  const [providerDescription, setProviderDescription] = useState('');
  const [providerCity, setProviderCity] = useState('Harare');
  const [providerCountry, setProviderCountry] = useState('ZW');
  const [providerCategory, setProviderCategory] = useState('plumbing-leak-detection');
  const account = useAccount();
  const savedProvider = useProviderProfile(account.user?.id, modal === 'provider');

  useEffect(() => {
    setProviderBusiness('');
    setProviderDescription('');
    setProviderCity('Harare');
    setProviderCountry('ZW');
    setProviderCategory('plumbing-leak-detection');
    setPassword('');
    setEmail('');
    setFullName('');
    setAge('');
  }, [account.user?.id]);

  useEffect(() => { setNotice(''); }, [modal]);
  useEffect(() => { localStorage.setItem('trolley-discovery-country', location); }, [location]);
  const discoveryCountries = useMemo(() => [{ value: '', label: 'Anywhere' }, ...countries.map(c => ({ value: c.value, label: c.label, hint: c.value, keywords: regions.find(r => r.value === c.region)?.label })).sort((a, b) => a.label.localeCompare(b.label))], []);
  const providerOptions = useMemo(() => categories.filter(c => c.parentId).map(c => { const parent = categories.find(p => p.id === c.parentId); const slug = `${parent?.name ?? ''}-${c.name}`.toLowerCase().replace(/&/g,'and').replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,''); return { ...c, slug, family: parent?.name ?? '' }; }), []);
  const categoryPickerOptions = useMemo(() => providerOptions.map(c => ({ value: c.slug, label: c.name, hint: c.family })), [providerOptions]);
  const directoryPickerOptions = useMemo(() => providerOptions.map(c => ({ value: c.name, label: c.name, hint: c.family, keywords: c.slug })), [providerOptions]);
  function changeProviderCountry(code: string) { if (code !== providerCountry) setProviderCity(''); setProviderCountry(code); }

  useEffect(() => { document.documentElement.dataset.theme = dark ? 'dark' : 'light'; localStorage.setItem('trolley-theme', dark ? 'dark' : 'light'); }, [dark]);
  const filteredCategories = useMemo(() => {
    const q = query.trim().toLowerCase();
    return categories.filter(c => c.parentId && (!q || c.name.toLowerCase().includes(q) || (categories.find(p => p.id === c.parentId)?.name.toLowerCase().includes(q))));
  }, [query]);
  const familyNames = categoryFamilies.map(f => f[0]);

  function consent(choice: 'accepted' | 'rejected') { localStorage.setItem('trolley-consent', choice); setCookieChoice(choice); }
  function chooseCategory(name: string) { setActiveCategory(name); setQuery(name === 'All services' ? '' : name); window.scrollTo({ top: 0, behavior: 'smooth' }); }
  async function submitAuth(e: React.FormEvent) {
    e.preventDefault(); setNotice('');
    if (modal === 'signup') {
      const d = new Date(age); const today = new Date(); let years = today.getFullYear() - d.getFullYear();
      if (!age || Number.isNaN(d.getTime()) || d > today) { setNotice('Enter a valid date of birth.'); return; }
      if (new Date(today.getFullYear(), today.getMonth(), today.getDate()) < new Date(today.getFullYear(), d.getMonth(), d.getDate())) years--;
      if (years < 13) { setNotice('You must be at least 13 years old to create an account. No account data has been saved.'); return; }
    }
    if (!supabase) { setNotice('Supabase is not connected yet. Add your project URL and publishable key to the .env file to enable accounts.'); return; }
    setBusy(true);
    try {
      const result = modal === 'signup'
        ? await supabase.auth.signUp({ email: email.trim(), password, options: { emailRedirectTo: authRedirectUrl(), data: { full_name: fullName.trim(), date_of_birth: age } } })
        : await supabase.auth.signInWithPassword({ email: email.trim(), password });
      if (result.error) throw result.error;
      setPassword('');
      setNotice(result.data.session ? 'You are signed in.' : 'Account request received. Check your email to verify your address, then sign in.');
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Unable to connect. Please try again.');
    } finally { setBusy(false); }
  }
  async function googleSignIn() {
    if (!supabase) { setNotice('Connect Supabase and configure Google OAuth before using Google sign-in.'); return; }
    setBusy(true);
    try {
      const { error } = await supabase.auth.signInWithOAuth({ provider: 'google', options: { redirectTo: authRedirectUrl() } });
      if (error) throw error;
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Unable to start Google sign-in. Please try again.');
    } finally { setBusy(false); }
  }
  async function signOut() {
    if (!supabase) return;
    setBusy(true);
    try {
      const { error } = await supabase.auth.signOut({ scope: 'local' });
      if (error) throw error;
      setModal(null);
    } catch (error) {
      setModal('signin');
      setNotice(error instanceof Error ? error.message : 'Unable to sign out. Please try again.');
    } finally { setBusy(false); }
  }
  async function submitProvider(e: React.FormEvent) {
    e.preventDefault(); setNotice('');
    if (!supabase) { setNotice('Connect Supabase first. Your provider details have not been saved.'); return; }
    setBusy(true);
    try {
      const { data: sessionData, error: sessionError } = await supabase.auth.getSession();
      if (sessionError) throw sessionError;
      if (!sessionData.session) { setNotice('Please create an account or sign in before registering a business.'); return; }
      const { data, error } = await supabase.rpc('create_provider_profile', { p_business_name: providerBusiness, p_description: providerDescription, p_city: providerCity, p_country_code: providerCountry, p_category_slug: providerCategory });
      if (error) throw error;
      if (typeof data !== 'string' || !data) throw new Error('The server did not return a provider reference. Reopen this form to check whether your draft was saved before retrying.');
      savedProvider.refresh();
      setNotice(`Your provider profile has been saved as a draft (reference ${data.slice(0,8)}). It is not public until reviewed and approved.`);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Unable to confirm whether your draft was saved. Reopen this form to check before retrying.');
    } finally { setBusy(false); }
  }

  return <div className="app-shell">
    <header className="site-header">
      <div className="nav-wrap">
        <a className="brand" href="#top" aria-label="Trolley home"><img src="/trolley-logo.jpg" alt=""/><span>trolley<span className="brand-dot">.</span></span></a>
        <nav className={`nav-links ${mobileMenu ? 'open' : ''}`}>
          <a href="#explore" onClick={() => setMobileMenu(false)}>Explore services</a><a href="#how" onClick={() => setMobileMenu(false)}>How it works</a><a href="#trust" onClick={() => setMobileMenu(false)}>Trust & safety</a>
          <button className="mobile-theme" onClick={() => setDark(!dark)}>{dark ? <Sun size={17}/> : <Moon size={17}/>} Theme</button>
        </nav>
        <div className="nav-actions"><button className="icon-btn theme-toggle" aria-label="Toggle theme" onClick={() => setDark(!dark)}>{dark ? <Sun size={18}/> : <Moon size={18}/>}</button>{account.user ? <><button className="text-btn sign-in" disabled={busy} onClick={signOut}>Sign out</button><button className="btn btn-dark nav-cta" onClick={() => setModal('provider')}>My business <Store size={16}/></button></> : <><button className="text-btn sign-in" disabled={account.loading} onClick={() => {setModal('signin');setNotice('')}}>{account.loading ? 'Loading account…' : 'Sign in'}</button><button className="btn btn-dark nav-cta" disabled={account.loading} onClick={() => {setModal('signup');setNotice('')}}>Get started <ArrowUpRight size={16}/></button></>}<button className="icon-btn menu-toggle" aria-label="Open menu" onClick={() => setMobileMenu(!mobileMenu)}>{mobileMenu ? <X/> : <Menu/>}</button></div>
      </div>
    </header>

    <main id="top">
      {account.error && <p className="notice-message section-wrap" role="alert">{account.error}</p>}
      <section className="hero section-wrap">
        <div className="hero-copy"><div className="eyebrow"><span className="eyebrow-line"/> YOUR LOCAL SERVICES, SORTED</div><h1>Find the right<br/><span>people for the job.</span></h1><p className="hero-lead">From the quick fix to the big project. Discover local professionals, compare your options and get the help you need.</p>
          <div className="search-panel"><div className="search-field"><Search size={20}/><input aria-label="Search services" value={query} onChange={e => setQuery(e.target.value)} onKeyDown={e => e.key === 'Enter' && document.getElementById('explore')?.scrollIntoView({behavior:'smooth'})} placeholder="What service do you need?"/><kbd>⌘ K</kbd></div><div className="search-divider"/><div className="location-field"><SearchSelect compact label="Location" icon={<MapPin size={17}/>} value={location} onChange={setLocation} options={discoveryCountries} placeholder="Anywhere" searchPlaceholder="Search country or ISO code" testId="discovery-country"/></div><button className="btn btn-orange search-submit" onClick={() => document.getElementById('explore')?.scrollIntoView({behavior:'smooth'})}>Find a pro <ArrowRight size={17}/></button></div>
          <div className="hero-meta"><span><ShieldCheck size={16}/> Real providers. Real profiles.</span><span className="meta-dot"/><span>No pressure. Just options.</span></div>
          <p className="discovery-note" data-testid="text-discovery-location">{location ? `Exploring from ${getCountryLabel(location)}, ${regions.find(r => r.value === getRegionForCountry(location))?.label ?? ''}.` : 'Exploring from anywhere.'} Saved on this device only. Directory results are service types, not providers in that location.</p>
        </div>
        <div className="hero-art" aria-label="Trolley service discovery illustration"><div className="art-orbit orbit-one"/><div className="art-orbit orbit-two"/><div className="art-center"><div className="art-icon"><img src="/trolley-logo.jpg" alt="Trolley shopping cart logo"/></div><div className="art-caption"><span className="art-caption-dot"/> The right help is closer than you think</div></div><div className="float-card float-card-top"><span className="float-icon green"><Wrench size={17}/></span><span><b>Home repairs</b><small>Find a local specialist</small></span><ArrowUpRight size={16}/></div><div className="float-card float-card-bottom"><span className="float-icon orange"><BriefcaseBusiness size={17}/></span><span><b>Business services</b><small>Experts for every stage</small></span><ArrowUpRight size={16}/></div><div className="art-spark spark-a">✳</div><div className="art-spark spark-b">✦</div></div>
      </section>

      <section className="trust-strip"><div className="section-wrap trust-inner"><div><ShieldCheck size={19}/><span>Built around trust</span></div><div><LockKeyhole size={18}/><span>Your data stays yours</span></div><div><MapPin size={18}/><span>Zimbabwe first. Everywhere next.</span></div><div><MessageCircle size={18}/><span>Connect directly with providers</span></div></div></section>

      <section className="explore-section section-wrap" id="explore"><div className="section-heading"><div><div className="eyebrow"><span className="eyebrow-line"/> DISCOVER WHAT YOU NEED</div><h2>What can we help<br className="mobile-break"/> you <span>find?</span></h2></div><button className="view-all" onClick={() => {setActiveCategory('All services');setQuery('');setShowAll(!showAll)}}>{showAll ? 'Show less' : 'Browse all services'} <ArrowUpRight size={17}/></button></div>
        <div className="category-grid">{familyHighlights.map((item, i) => {const Icon=item.icon;return <button className={`category-card cat-${i}`} key={item.name} onClick={() => chooseCategory(familyNames[i] || 'All services')}><span className="category-icon"><Icon size={22}/></span><span className="category-info"><b>{item.name}</b><small>{item.detail}</small></span><ArrowUpRight className="category-arrow" size={18}/></button>})}</div>
        <div className="category-browser"><div className="browser-top"><div><span className="browser-kicker">SERVICE DIRECTORY</span><h3>Explore the directory</h3></div><div className="browser-tools"><SearchSelect compact label="Jump to a service type" value={directoryPickerOptions.some(o => o.value === query) ? query : ''} onChange={v => { setQuery(v); setActiveCategory(v); }} options={directoryPickerOptions} placeholder="Find a service type" searchPlaceholder="Search service or family" maxVisible={60} testId="directory-category"/><span className="count-pill">{subcategoryCount.toLocaleString()} service types indexed</span></div></div><div className="filter-row"><button className={`filter-chip ${activeCategory==='All services'?'selected':''}`} onClick={() => chooseCategory('All services')}>All services</button>{familyNames.slice(0,8).map(n=><button key={n} className={`filter-chip ${activeCategory===n?'selected':''}`} onClick={() => chooseCategory(n)}>{n}</button>)}</div>
          {query.trim() ? <div className="search-results"><div className="results-label">MATCHING SERVICE TYPES <span>{filteredCategories.length} results</span></div>{filteredCategories.length ? <div className="result-chips">{filteredCategories.slice(0,24).map(c=><button key={c.id} onClick={() => {setQuery(c.name);setActiveCategory(c.name)}}>{c.name}<ArrowUpRight size={13}/></button>)}</div> : <div className="empty-results">No matching service types yet. Try a broader search or another phrase.</div>}</div> : <div className="directory-note"><div className="note-icon"><Search size={18}/></div><div><b>Search over 1,000 service specialities</b><p>Type a service above to see matching categories. Provider results will appear here once real providers publish approved listings.</p></div><button className="subtle-link" onClick={() => setShowAll(!showAll)}>{showAll ? 'Hide categories' : 'See categories'} <ChevronDown size={15}/></button></div>}
          {showAll && <div className="all-families">{categoryFamilies.map(([family,...subs])=><div className="family-list" key={family}><b>{family}</b><div>{subs.map(s=><button key={s} onClick={()=>{setQuery(s);setActiveCategory(family)}}>{s}</button>)}</div></div>)}</div>}
        </div>
      </section>

      <section className="how-section" id="how"><div className="section-wrap"><div className="section-heading how-heading"><div><div className="eyebrow"><span className="eyebrow-line"/> SIMPLE BY DESIGN</div><h2>From “I need help”<br/>to <span>job done.</span></h2></div><p className="section-aside">A straightforward way to find the right professional and get the conversation started.</p></div><div className="steps-grid"><div className="step-card"><span className="step-number">01</span><div className="step-icon"><Search size={23}/></div><h3>Tell us what you need</h3><p>Search by service, speciality or location. Narrow things down to what matters to you.</p></div><div className="step-card"><span className="step-number">02</span><div className="step-icon"><UserRound size={23}/></div><h3>Explore real providers</h3><p>Review published profiles, service areas, portfolios and genuine customer feedback.</p></div><div className="step-card"><span className="step-number">03</span><div className="step-icon"><MessageCircle size={23}/></div><h3>Connect with confidence</h3><p>Send an enquiry, discuss the work and compare quotations before making a decision.</p></div></div></div></section>

      <section className="trust-section section-wrap" id="trust"><div className="trust-card"><div className="trust-copy"><div className="eyebrow"><span className="eyebrow-line"/> TRUST IS THE STARTING POINT</div><h2>Good work starts<br/>with <span>good people.</span></h2><p>Trolley is designed to make finding help feel clearer and safer. Provider profiles are reviewed before they appear publicly, and customer reviews are tied to eligible service interactions.</p><div className="trust-points"><div><BadgeCheck size={19}/><span><b>Reviewed listings</b><small>Profiles go through a publishing review.</small></span></div><div><LockKeyhole size={19}/><span><b>Private by default</b><small>Your personal information is protected.</small></span></div><div><Star size={19}/><span><b>Genuine feedback</b><small>No invented ratings or testimonials.</small></span></div></div><button className="btn btn-dark" onClick={() => setModal('privacy')}>Our privacy approach <ArrowRight size={16}/></button></div><div className="trust-visual"><div className="shield-graphic"><div className="shield-ring ring-a"/><div className="shield-ring ring-b"/><div className="shield-core"><ShieldCheck size={76}/></div><span className="shield-label label-one">Privacy first</span><span className="shield-label label-two">Real profiles</span><span className="shield-label label-three">Safer contact</span></div><div className="visual-foot">DISCOVER <span>·</span> GROW <span>·</span> TRUST</div></div></div></section>

      <section className="provider-cta section-wrap"><div className="provider-banner"><div className="provider-mark"><Store size={25}/></div><div><div className="eyebrow">FOR SERVICE PROFESSIONALS</div><h2>Great at what you do?<br/><span>Get found on Trolley.</span></h2><p>Create a business profile, showcase your work and connect with people looking for your services.</p></div><button className="btn btn-orange" onClick={() => setModal('provider')}>List your business <ArrowUpRight size={17}/></button><div className="banner-decoration">✳</div></div></section>

      <section className="bottom-note section-wrap"><div><span className="bottom-note-icon"><CircleHelp size={19}/></span><span><b>Just getting started?</b><small>Trolley is being built with real providers and transparent information. No made-up listings, reviews or statistics.</small></span></div><a href="mailto:hello@trolley.example">Get in touch <ArrowUpRight size={15}/></a></section>
    </main>

    <footer className="footer"><div className="section-wrap footer-main"><div className="footer-brand"><a className="brand" href="#top"><img src="/trolley-logo.jpg" alt=""/><span>trolley<span className="brand-dot">.</span></span></a><p>Find the right people<br/>for the job.</p></div><div className="footer-col"><b>Discover</b><a href="#explore">Explore services</a><a href="#how">How it works</a><a href="#trust">Trust & safety</a></div><div className="footer-col"><b>For providers</b><button onClick={() => setModal('provider')}>List your business</button><button onClick={() => setModal('signin')}>Provider sign in</button><a href="#explore">Service categories</a></div><div className="footer-col"><b>Information</b><button onClick={() => setModal('privacy')}>Privacy policy</button><button onClick={() => setModal('terms')}>Terms of service</button><a href="mailto:hello@trolley.example">Contact</a></div></div><div className="section-wrap footer-bottom"><span>© {new Date().getFullYear()} Trolley. All rights reserved.</span><span>Built for trust. Designed to connect.</span></div></footer>

    {!cookieChoice && <div className="consent-banner"><div className="consent-copy"><span className="consent-icon"><LockKeyhole size={18}/></span><span><b>Your privacy matters</b><small>We use essential storage to make Trolley work. Optional analytics stay off unless you choose to allow them. <button onClick={() => setModal('privacy')}>Learn more</button></small></span></div><div className="consent-actions"><button className="btn btn-outline" onClick={() => consent('rejected')}>Reject optional</button><button className="btn btn-dark" onClick={() => consent('accepted')}>Accept all</button></div></div>}

    {account.recovery !== 'none' && <div className="modal-backdrop"><div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="modal-title">
      <PasswordRecovery mode={account.recovery} onSignIn={() => { account.finishRecovery(); setPassword(''); setModal('signin'); }}/>
    </div></div>}
    {account.recovery === 'none' && modal && <div className="modal-backdrop" role="presentation" onMouseDown={e => {if(e.target===e.currentTarget)setModal(null)}}><div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="modal-title"><button className="modal-close" onClick={() => setModal(null)} aria-label="Close"><X size={20}/></button>
      {modal === 'forgot' && <PasswordRecovery mode="request" onSignIn={() => setModal('signin')}/>}
      {(modal==='signin'||modal==='signup') && <><div className="modal-logo"><img src="/trolley-logo.jpg" alt=""/></div><div className="modal-eyebrow">WELCOME TO TROLLEY</div><h2 id="modal-title">{modal==='signup'?'Create your account':'Good to see you again.'}</h2><p className="modal-intro">{modal==='signup'?'Join Trolley to connect with trusted local professionals.':'Sign in to manage your enquiries and saved providers.'}</p><form onSubmit={submitAuth} className="auth-form">{modal==='signup'&&<label>Full name<input required value={fullName} onChange={e=>setFullName(e.target.value)} placeholder="Your full name"/></label>}<label>Email address<input required type="email" value={email} onChange={e=>setEmail(e.target.value)} placeholder="you@example.com"/></label><label>Password<div className="password-input"><input required minLength={8} type={passwordVisible?'text':'password'} value={password} onChange={e=>setPassword(e.target.value)} placeholder="At least 8 characters"/><button type="button" onClick={()=>setPasswordVisible(!passwordVisible)}>{passwordVisible?<EyeOff size={17}/>:<Eye size={17}/>}</button></div></label>
      {modal === 'signin' && <p className="modal-switch" style={{ margin: 0, textAlign: 'right' }}><button type="button" data-testid="button-forgot-password" disabled={busy} onClick={() => {setPassword('');setModal('forgot');}}>Forgot password?</button></p>}
      {modal==='signup'&&<label>Date of birth<input required type="date" value={age} onChange={e=>setAge(e.target.value)} max={new Date().toISOString().slice(0,10)}/><small className="field-hint">You must be at least 13 years old. Under-13 data is not saved.</small></label>}<button className="btn btn-dark auth-submit" disabled={busy}>{busy?'Please wait…':modal==='signup'?'Create account':'Sign in'} <ArrowRight size={16}/></button></form><div className="auth-divider"><span/>or<span/></div><button className="google-btn" onClick={googleSignIn}><span className="google-g">G</span> Continue with Google</button>{notice&&<div className="notice-message" role="status">{notice}</div>}<p className="modal-switch">{modal==='signup'?'Already have an account?':'New to Trolley?'} <button onClick={()=>{setModal(modal==='signup'?'signin':'signup');setNotice('')}}>{modal==='signup'?'Sign in':'Create account'}</button></p><p className="legal-mini">By continuing, you agree to our <button onClick={()=>setModal('terms')}>Terms</button> and <button onClick={()=>setModal('privacy')}>Privacy Policy</button>.</p></>}
      {modal==='provider' && <>
        <div className="modal-logo"><Store size={25}/></div><div className="modal-eyebrow">GROW WITH TROLLEY</div>
        <h2 id="modal-title">{savedProvider.profile ? 'Your saved provider profile.' : 'Create your provider profile.'}</h2>
        {account.loading || savedProvider.loading ? <p role="status">Loading your saved profile…</p> : savedProvider.error ? <div className="notice-message" role="alert"><p>We could not check your saved provider profile: {savedProvider.error}</p><button className="btn btn-outline" onClick={savedProvider.refresh}>Retry</button></div> : savedProvider.profile ? <ProviderEditor key={`${account.user?.id}:${savedProvider.profile.id}`} profile={savedProvider.profile} options={providerOptions} refresh={saved => { setNotice(saved ? 'Changes saved. Reloading your stored details and review status.' : ''); savedProvider.refresh(); }}/> : !account.user ? <>
          <p className="modal-intro">Sign in or create an account before saving a private provider draft.</p>
          <button className="btn btn-dark auth-submit" onClick={() => setModal('signin')}>Sign in <ArrowRight size={16}/></button>
          <button className="subtle-link" onClick={() => setModal('signup')}>Create account</button>
        </> : <>
          <p className="modal-intro">Start with your business details. Trolley saves this as a private draft; an administrator must review and approve it before customers can find it.</p>
          <form className="auth-form" onSubmit={submitProvider}>
            <label>Business name<input required minLength={2} maxLength={120} value={providerBusiness} onChange={e=>setProviderBusiness(e.target.value)} placeholder="e.g. Brightline Electrical"/></label>
            <SearchSelect label="Main service category" required value={providerCategory} onChange={setProviderCategory} options={categoryPickerOptions} placeholder="Choose a service category" searchPlaceholder="Search service or family" testId="provider-category"/>
            <RegionCountryFields required country={providerCountry} onCountry={changeProviderCountry} testId="provider"/>
            <label>City<input required minLength={2} maxLength={100} value={providerCity} onChange={e=>setProviderCity(e.target.value)} placeholder={providerCountry ? `A city in ${getCountryLabel(providerCountry)}` : 'Choose a country first'}/></label>
            <label>Short business description<textarea rows={3} maxLength={1200} value={providerDescription} onChange={e=>setProviderDescription(e.target.value)} placeholder="Tell customers what you do and the areas you serve."/></label>
            <button className="btn btn-dark auth-submit" disabled={busy}>{busy?'Saving draft…':'Save provider draft'} <ArrowRight size={16}/></button>
          </form>
        </>}
        {notice&&<div className="notice-message" role="status">{notice}</div>}
      </>}
      {(modal==='privacy'||modal==='terms') && <><div className="modal-eyebrow">TROLLEY POLICIES · DRAFT</div><h2 id="modal-title">{modal==='privacy'?'Privacy at Trolley':'Trolley terms of service'}</h2><p className="modal-intro">{modal==='privacy'?'This initial privacy notice describes the intended data principles. A jurisdiction-specific legal review and final policy are required before public launch.':'These draft terms outline the intended marketplace relationship. They are not final legal terms and require professional review before launch.'}</p><div className="policy-scroll">{modal==='privacy'?<><h3>Information and purpose</h3><p>Trolley is designed to collect only information needed to create accounts, operate provider profiles, support enquiries and protect the marketplace. Account details, service enquiries and uploaded profile content are handled according to role-based access rules.</p><h3>Consent and analytics</h3><p>Non-essential analytics and tracking must remain disabled until a user gives consent. Rejecting optional analytics must be as easy as accepting them. Essential application storage may still be used to provide core functionality.</p><h3>Security and access</h3><p>Private records are protected by server-side authorization and database Row Level Security. Users should only be able to access their own private data and conversations they participate in. Verification documents are private.</p><h3>Your choices</h3><p>Users will be able to request access, correction, export or deletion of their personal information where supported by applicable law. Contact details and final retention periods will be published before launch.</p></>:<><h3>Marketplace role</h3><p>Trolley helps customers discover independent service providers. Providers are responsible for the services, qualifications, quotations and work they offer. A directory listing is not a guarantee of service quality.</p><h3>Listings and reviews</h3><p>Providers must supply accurate information and may only publish listings after required checks. Reviews must reflect genuine eligible interactions and may be moderated for abuse or policy violations.</p><h3>Quotations and payments</h3><p>A quotation is not a confirmed booking or payment. Subscription prices, renewal dates, cancellation methods and applicable refund terms must be shown clearly before purchase. Payment status is confirmed by the payment provider, not a browser message.</p><h3>Safe use</h3><p>Users must not impersonate others, submit fraudulent listings, harass other users or misuse personal information. Reporting, blocking and appeals will be provided as marketplace features are completed.</p></>}</div><button className="btn btn-dark auth-submit" onClick={()=>setModal(null)}>Close</button></>}
    </div></div>}
  </div>;
}

export default App;
