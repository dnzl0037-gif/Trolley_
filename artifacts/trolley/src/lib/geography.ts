/// <reference lib="es2021.intl" />

// ISO 3166-1 countries and territories, grouped geographically for browsing.
// A continent is derived from country_code; it must not be written into the
// provider_locations.region column, which represents a province/state.
export const regions = [
  { value: 'Africa', label: 'Africa' },
  { value: 'Asia', label: 'Asia' },
  { value: 'Europe', label: 'Europe' },
  { value: 'North America', label: 'North America' },
  { value: 'South America', label: 'South America' },
  { value: 'Oceania', label: 'Oceania' },
  { value: 'Antarctica', label: 'Antarctica' },
] as const;

const countryCodes: Record<typeof regions[number]['value'], string> = {
  Africa: 'DZ AO BJ BW BF BI CV CM CF TD KM CG CD CI DJ EG GQ ER SZ ET GA GM GH GN GW KE LS LR LY MG MW ML MR MU YT MA MZ NA NE NG RE RW SH ST SN SC SL SO ZA SS SD TZ TG TN UG EH ZM ZW',
  Asia: 'AF AM AZ BH BD BT BN KH CN CX CC GE HK IN ID IR IQ IL JP JO KZ KP KR KW KG LA LB MO MY MV MN MM NP OM PK PS PH QA SA SG LK SY TW TJ TH TL TR TM AE UZ VN YE IO',
  Europe: 'AX AL AD AT BY BE BA BG HR CY CZ DK EE FO FI FR DE GI GR GG VA HU IS IE IM IT JE LV LI LT LU MT MD MC ME NL MK NO PL PT RO RU SM RS SK SI ES SJ SE CH UA GB',
  'North America': 'AI AG AW BS BB BZ BM BQ CA KY CR CU CW DM DO SV GL GD GP GT HT HN JM MQ MX MS NI PA PR BL KN LC MF PM VC SX TT TC US VG VI',
  'South America': 'AR BO BR CL CO EC FK GF GY PY PE SR UY VE',
  Oceania: 'AS AU CK FJ PF GU KI MH FM NR NC NZ NU NF MP PW PG PN WS SB TK TO TV UM VU WF',
  Antarctica: 'AQ BV TF HM GS',
};

const names = new Intl.DisplayNames(['en'], { type: 'region' });

export type Country = { value: string; label: string; region: string };

export const countries: Country[] = regions.flatMap(region =>
  countryCodes[region.value].split(' ').map(value => ({
    value,
    label: names.of(value) ?? value,
    region: region.value,
  })),
).sort((a, b) => a.label.localeCompare(b.label, 'en'));

const countriesByCode = new Map(countries.map(country => [country.value, country]));

export function getRegionForCountry(code: string): string {
  return countriesByCode.get(code.trim().toUpperCase())?.region ?? '';
}

export function getCountryLabel(code: string): string {
  return countriesByCode.get(code.trim().toUpperCase())?.label ?? code;
}
