import { useRef, useState } from 'react';
import { supabase } from '../lib/supabase';
import type { ProviderProfile } from '../lib/useProviderProfile';

type CategoryOption = { slug: string; name: string; family: string };

export function ProviderEditor({ profile, options, refresh }: {
  profile: ProviderProfile;
  options: CategoryOption[];
  refresh: (saved?: boolean) => void;
}) {
  const service = profile.services[0];
  const location = profile.provider_locations[0];
  const [business, setBusiness] = useState(profile.business_name);
  const [description, setDescription] = useState(profile.description ?? '');
  const [category, setCategory] = useState(service?.categories?.slug ?? '');
  const [city, setCity] = useState(location?.city ?? '');
  const [country, setCountry] = useState(location?.country_code ?? '');
  const [busy, setBusy] = useState(false);
  const saving = useRef(false);
  const [message, setMessage] = useState('');
  const [failed, setFailed] = useState(false);
  const editable = profile.services.length === 1 && profile.provider_locations.length === 1
    && ['draft', 'pending_review'].includes(service.status);

  async function save(submit: boolean) {
    if (!supabase || saving.current || !editable) return;
    saving.current = true;
    setBusy(true);
    setMessage('');
    setFailed(false);
    try {
      const { data, error } = await supabase.rpc('update_provider_draft', {
        p_provider_id: profile.id,
        p_business_name: business,
        p_description: description,
        p_city: city,
        p_country_code: country,
        p_category_slug: category,
        p_submit: submit,
      });
      if (error) throw error;
      if (data !== profile.id) throw new Error('The server did not confirm this profile. Reload your saved details before retrying.');
      // Re-read all related records; never treat the submitted form as saved data.
      refresh(true);
    } catch (error) {
      setFailed(true);
      const detail = error && typeof error === 'object' && 'message' in error ? error.message : null;
      setMessage(typeof detail === 'string' ? detail : 'Unable to confirm this save. Reload your saved details before retrying.');
    } finally {
      saving.current = false;
      setBusy(false);
    }
  }

  return <div className="auth-form">
    <p role="status">Listing status: <strong>{service?.status.replace(/_/g, ' ') ?? 'Unavailable'}</strong></p>
    <p className="field-hint">Verification: {profile.verification_status.replace(/_/g, ' ')}. Only Trolley administrators can approve verification or publish your listing. Submitting for review does not make it public.</p>
    {!editable ? <p role="alert">This profile cannot be edited here. Editing supports one draft or pending-review service and one location. Contact Trolley for other listing states or additional services and locations.</p> : <form className="auth-form" onSubmit={event => {
      event.preventDefault();
      const submitter = (event.nativeEvent as SubmitEvent).submitter;
      void save(submitter instanceof HTMLButtonElement && submitter.value === 'review');
    }}>
      <fieldset disabled={busy} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }} className="auth-form">
        <label>Business name<input required minLength={2} maxLength={120} value={business} onChange={event => setBusiness(event.target.value)}/></label>
        <label>Main service category<select required value={category} onChange={event => setCategory(event.target.value)}>
          {!options.some(option => option.slug === category) && <option value={category} disabled>Choose an active service category</option>}
          {options.map(option => <option key={option.slug} value={option.slug}>{option.family} · {option.name}</option>)}
        </select></label>
        <label>City<input required minLength={2} maxLength={100} value={city} onChange={event => setCity(event.target.value)}/></label>
        <label>Country code<input required pattern="[A-Z]{2}" maxLength={2} value={country} onChange={event => setCountry(event.target.value.toUpperCase())}/><small className="field-hint">Two-letter code, for example ZW or ZA.</small></label>
        <label>Short business description<textarea rows={3} maxLength={1200} value={description} onChange={event => setDescription(event.target.value)}/></label>
        <button type="submit" value="save" className="btn btn-dark auth-submit">{busy ? 'Saving…' : 'Save changes'}</button>
        <button type="submit" value="review" className="btn btn-outline" disabled={service.status === 'pending_review'}>Submit for review</button>
      </fieldset>
      {service.status === 'pending_review' && <p className="field-hint">Your listing is awaiting review. Saved changes remain pending review.</p>}
    </form>}
    {message && <p className="notice-message" role={failed ? 'alert' : 'status'}>{message}</p>}
    <button type="button" className="subtle-link" disabled={busy} onClick={() => refresh()}>Reload saved details</button>
    <p className="field-hint">Saved reference: {profile.id}. These details were loaded from your account. After saving, the form reloads the stored details and current review status.</p>
  </div>;
}
