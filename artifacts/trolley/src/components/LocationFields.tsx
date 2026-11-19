import { useMemo, useState } from 'react';
import { countries, getRegionForCountry, regions } from '../lib/geography';
import { SearchSelect } from './SearchSelect';

/** Continent (derived, UI-only) + country picker. Persists only the country code. */
export function RegionCountryFields({ country, onCountry, required, disabled, compact, testId = 'location' }: {
  country: string; onCountry: (code: string) => void; required?: boolean; disabled?: boolean; compact?: boolean; testId?: string;
}) {
  const derived = country ? getRegionForCountry(country) : '';
  const [region, setRegion] = useState(derived);
  const shownRegion = country ? derived : region;
  const regionOptions = useMemo(() => [{ value: '', label: 'All regions' }, ...regions.map(r => ({ value: r.value, label: r.label }))], []);
  const countryOptions = useMemo(() => countries
    .filter(c => !shownRegion || c.region === shownRegion)
    .map(c => ({ value: c.value, label: c.label, hint: c.value, keywords: regions.find(r => r.value === c.region)?.label }))
    .sort((a, b) => a.label.localeCompare(b.label)), [shownRegion]);

  function changeRegion(next: string) {
    setRegion(next);
    if (country && next && getRegionForCountry(country) !== next) onCountry('');
  }

  return <div className={`region-country ${compact ? 'is-compact' : ''}`}>
    <SearchSelect label="Region" value={shownRegion} onChange={changeRegion} options={regionOptions} placeholder="All regions" searchPlaceholder="Search continents" disabled={disabled} compact={compact} testId={`${testId}-region`}/>
    <SearchSelect label="Country" value={country} onChange={onCountry} options={countryOptions} placeholder={shownRegion ? 'Choose a country' : 'Choose a country or territory'} searchPlaceholder="Search country or ISO code" required={required} disabled={disabled} compact={compact} testId={`${testId}-country`}/>
  </div>;
}
