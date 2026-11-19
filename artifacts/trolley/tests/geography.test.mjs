import { test } from 'node:test';
import assert from 'node:assert/strict';
import { countries, regions, getCountryLabel, getRegionForCountry } from '../src/lib/geography.ts';

test('all 249 ISO country and territory codes are unique and named', () => {
  assert.equal(countries.length, 249);
  assert.equal(new Set(countries.map(country => country.value)).size, 249);
  for (const country of countries) {
    assert.match(country.value, /^[A-Z]{2}$/);
    assert.notEqual(country.label, country.value);
    assert.ok(regions.some(region => region.value === country.region));
  }
});

test('country lookups derive continents without needing another saved field', () => {
  for (const [code, region] of [
    ['ZW', 'Africa'], ['IN', 'Asia'], ['GB', 'Europe'], ['US', 'North America'],
    ['BR', 'South America'], ['AU', 'Oceania'], ['AQ', 'Antarctica'],
  ]) {
    assert.equal(getRegionForCountry(code), region);
  }
  assert.equal(getCountryLabel(' zw '), 'Zimbabwe');
  assert.equal(getRegionForCountry('unknown'), '');
  assert.equal(getCountryLabel('unknown'), 'unknown');
});
