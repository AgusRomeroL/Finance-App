// Prueba de las reglas de Firestore de Finance-App contra el emulador.
// No necesita credenciales: el emulador evalua el mismo firestore.rules real.
import {
  initializeTestEnvironment, assertFails, assertSucceeds,
} from '@firebase/rules-unit-testing'
import { doc, getDoc, setDoc } from 'firebase/firestore'
import { readFileSync } from 'node:fs'

const HID = 'hh_test'
const MEMBER_READABLE = ['members', 'categories', 'wallets']
const LEDGER_ONLY = [
  'expenses', 'quincenas', 'income_source', 'wallet_transfer',
  'savings_goal', 'loan', 'installment_plan', 'recurrence_template',
  'statement_import',
]

const env = await initializeTestEnvironment({
  projectId: 'rules-test',
  firestore: { rules: readFileSync('firestore.rules', 'utf8'), host: '127.0.0.1', port: 8085 },
})

// Siembra los docs de rol sin pasar por las reglas.
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore()
  await setDoc(doc(db, `households/${HID}/roles/u_owner`), { role: 'OWNER' })
  await setDoc(doc(db, `households/${HID}/roles/u_payer`), { role: 'PAYER' })
  await setDoc(doc(db, `households/${HID}/roles/u_member`), { role: 'MEMBER' })
  for (const c of [...MEMBER_READABLE, ...LEDGER_ONLY, 'proposals']) {
    await setDoc(doc(db, `households/${HID}/${c}/d1`), { id: 'd1', householdId: HID })
  }
  await setDoc(doc(db, `households/${HID}/expenses/e1/attributions/a1`), { id: 'a1' })
})

const as = (uid) => env.authenticatedContext(uid).firestore()
let pass = 0, fail = 0
const check = async (name, p) => {
  try { await p; pass++ } catch (e) { fail++; console.log('FALLA  ' + name + ': ' + e.message) }
}

for (const c of MEMBER_READABLE) {
  await check(`MEMBER lee ${c}`, assertSucceeds(getDoc(doc(as('u_member'), `households/${HID}/${c}/d1`))))
  await check(`MEMBER no escribe ${c}`, assertFails(setDoc(doc(as('u_member'), `households/${HID}/${c}/d2`), { x: 1 })))
  await check(`OWNER escribe ${c}`, assertSucceeds(setDoc(doc(as('u_owner'), `households/${HID}/${c}/d3`), { x: 1 })))
  await check(`PAYER escribe ${c}`, assertSucceeds(setDoc(doc(as('u_payer'), `households/${HID}/${c}/d4`), { x: 1 })))
}
for (const c of LEDGER_ONLY) {
  await check(`MEMBER NO lee ${c}`, assertFails(getDoc(doc(as('u_member'), `households/${HID}/${c}/d1`))))
  await check(`PAYER lee ${c}`, assertSucceeds(getDoc(doc(as('u_payer'), `households/${HID}/${c}/d1`))))
  await check(`OWNER escribe ${c}`, assertSucceeds(setDoc(doc(as('u_owner'), `households/${HID}/${c}/d2`), { x: 1 })))
}
await check('MEMBER NO lee attributions', assertFails(getDoc(doc(as('u_member'), `households/${HID}/expenses/e1/attributions/a1`))))
await check('PAYER lee attributions', assertSucceeds(getDoc(doc(as('u_payer'), `households/${HID}/expenses/e1/attributions/a1`))))
await check('MEMBER crea proposal', assertSucceeds(setDoc(doc(as('u_member'), `households/${HID}/proposals/p1`), { x: 1 })))
await check('MEMBER lee proposals', assertSucceeds(getDoc(doc(as('u_member'), `households/${HID}/proposals/d1`))))
for (const c of [...MEMBER_READABLE, ...LEDGER_ONLY, 'proposals']) {
  await check(`ajeno NO lee ${c}`, assertFails(getDoc(doc(as('u_ajeno'), `households/${HID}/${c}/d1`))))
}
await check('MEMBER lee el doc del hogar', assertSucceeds(getDoc(doc(as('u_member'), `households/${HID}`))))
await check('MEMBER lee su doc de rol', assertSucceeds(getDoc(doc(as('u_member'), `households/${HID}/roles/u_member`))))
await check('ajeno NO lee el doc del hogar', assertFails(getDoc(doc(as('u_ajeno'), `households/${HID}`))))
await check('coleccion no declarada cerrada (OWNER)', assertFails(getDoc(doc(as('u_owner'), `households/${HID}/coleccion_nueva/d1`))))

await env.cleanup()
console.log(`${pass}/${pass + fail} casos en verde`)
process.exit(fail ? 1 : 0)
