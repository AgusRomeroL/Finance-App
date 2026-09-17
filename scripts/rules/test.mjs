// Prueba de las reglas de Firestore de Finance-App contra el emulador.
// No necesita credenciales: el emulador evalua el mismo firestore.rules real.
//
// Fase 2 dejo 60 casos (lectura por coleccion). Fase 7 los convierte en una
// matriz rol x coleccion x operacion (get, list, create, update, delete) y
// anade los documentos de identidad: hogar, roles, invitaciones, propuestas,
// users/{uid} e invite_codes. Al anadir una coleccion al sync hay que anadirla
// a LEDGER_ONLY o a MEMBER_READABLE, y la matriz la cubre sola.
import {
  initializeTestEnvironment, assertFails, assertSucceeds,
} from '@firebase/rules-unit-testing'
import { collection, deleteDoc, doc, getDoc, getDocs, setDoc, updateDoc } from 'firebase/firestore'
import { readFileSync } from 'node:fs'

const HID = 'hh_test'
const OTHER_HID = 'hh_ajeno'
const MEMBER_READABLE = ['members', 'categories', 'wallets']
const LEDGER_ONLY = [
  'expenses', 'quincenas', 'income_source', 'wallet_transfer',
  'savings_goal', 'loan', 'installment_plan', 'recurrence_template',
  'statement_import',
]
const HOUSEHOLD_DATA = [...MEMBER_READABLE, ...LEDGER_ONLY]

const env = await initializeTestEnvironment({
  projectId: 'rules-test',
  firestore: { rules: readFileSync('firestore.rules', 'utf8'), host: '127.0.0.1', port: 8085 },
})

// Siembra sin pasar por las reglas: dos hogares, roles, invites y un doc por coleccion.
async function seed() {
  await env.clearFirestore()
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore()
    await setDoc(doc(db, `households/${HID}`), { name: 'Hogar de prueba', createdBy: 'u_owner' })
    await setDoc(doc(db, `households/${HID}/roles/u_owner`), { role: 'OWNER' })
    await setDoc(doc(db, `households/${HID}/roles/u_payer`), { role: 'PAYER' })
    await setDoc(doc(db, `households/${HID}/roles/u_member`), { role: 'MEMBER' })
    await setDoc(doc(db, `households/${HID}/roles/u_legacy`), { role: 'COLLABORATOR' })
    await setDoc(doc(db, `households/${HID}/invites/INV1PAYR`), { role: 'PAYER', linkedMemberId: 'm-benjamin', uses: 0, maxUses: 1 })
    await setDoc(doc(db, `households/${HID}/invites/INV2MEMB`), { role: 'MEMBER', uses: 0, maxUses: 1 })
    for (const c of [...HOUSEHOLD_DATA, 'proposals']) {
      await setDoc(doc(db, `households/${HID}/${c}/d1`), { id: 'd1', householdId: HID })
      await setDoc(doc(db, `households/${HID}/${c}/d2`), { id: 'd2', householdId: HID })
    }
    await setDoc(doc(db, `households/${HID}/expenses/e1/attributions/a1`), { id: 'a1' })
    await setDoc(doc(db, `households/${HID}/proposals/p_member`), { id: 'p_member', proposedBy: 'u_member' })
    // Hogar sin dueno: reclamable estampando createdBy.
    await setDoc(doc(db, `households/hh_huerfano`), { name: 'Semilla sin dueno' })
    // Hogar ajeno con su propio dueno.
    await setDoc(doc(db, `households/${OTHER_HID}`), { name: 'Ajeno', createdBy: 'u_ajeno' })
    await setDoc(doc(db, `households/${OTHER_HID}/roles/u_ajeno`), { role: 'OWNER' })
    await setDoc(doc(db, `households/${OTHER_HID}/expenses/x1`), { id: 'x1' })
    await setDoc(doc(db, `users/u_member`), { displayName: 'Colab', activeHouseholdId: HID })
    await setDoc(doc(db, `users/u_member/households/${HID}`), { role: 'MEMBER' })
    await setDoc(doc(db, `invite_codes/CODEOWN1`), { householdId: HID, role: 'MEMBER', uses: 0, createdBy: 'u_owner' })
    await setDoc(doc(db, `invite_codes/CODEAJN1`), { householdId: OTHER_HID, role: 'MEMBER', uses: 0, createdBy: 'u_ajeno' })
  })
}
await seed()

const as = (uid) => env.authenticatedContext(uid).firestore()
const anon = () => env.unauthenticatedContext().firestore()
let pass = 0, fail = 0
const check = async (name, p) => {
  try { await p; pass++ } catch (e) { fail++; console.log('FALLA  ' + name + ': ' + e.message) }
}
const ok = (name, p) => check(name, assertSucceeds(p))
const no = (name, p) => check(name, assertFails(p))

// ── Matriz rol x coleccion x operacion ────────────────────────────────────
// Cada fila: [uid, etiqueta, lee MEMBER_READABLE, lee LEDGER_ONLY, escribe].
const ROLES = [
  ['u_owner', 'OWNER', true, true, true],
  ['u_payer', 'PAYER', true, true, true],
  ['u_member', 'MEMBER', true, false, false],
  ['u_legacy', 'COLLABORATOR (alias legacy de MEMBER)', true, false, false],
  ['u_ajeno', 'ajeno (miembro de otro hogar)', false, false, false],
]

for (const [uid, label, readsBasic, readsLedger, writes] of ROLES) {
  for (const c of HOUSEHOLD_DATA) {
    const canRead = MEMBER_READABLE.includes(c) ? readsBasic : readsLedger
    const path = `households/${HID}/${c}`
    const expectRead = canRead ? ok : no
    await expectRead(`${label} get ${c}`, getDoc(doc(as(uid), `${path}/d1`)))
    await expectRead(`${label} list ${c}`, getDocs(collection(as(uid), path)))
    const expectWrite = writes ? ok : no
    await expectWrite(`${label} create ${c}`, setDoc(doc(as(uid), `${path}/nuevo_${uid}`), { x: 1 }))
    await expectWrite(`${label} update ${c}`, updateDoc(doc(as(uid), `${path}/d1`), { y: 2 }))
    await expectWrite(`${label} delete ${c}`, deleteDoc(doc(as(uid), `${path}/d2`)))
    // Se restaura lo borrado para la siguiente fila de la matriz.
    if (writes) {
      await env.withSecurityRulesDisabled(async (ctx) =>
        setDoc(doc(ctx.firestore(), `${path}/d2`), { id: 'd2', householdId: HID }))
    }
  }
  // Atribuciones: cuelgan del gasto y heredan su criterio.
  const attr = `households/${HID}/expenses/e1/attributions/a1`
  await (readsLedger ? ok : no)(`${label} get attributions`, getDoc(doc(as(uid), attr)))
  await (writes ? ok : no)(`${label} write attributions`, setDoc(doc(as(uid), `households/${HID}/expenses/e1/attributions/a_${uid}`), { x: 1 }))
}

// ── Sin sesion: nada ──────────────────────────────────────────────────────
await no('anonimo NO lee el hogar', getDoc(doc(anon(), `households/${HID}`)))
await no('anonimo NO lee members', getDoc(doc(anon(), `households/${HID}/members/d1`)))
await no('anonimo NO lee expenses', getDoc(doc(anon(), `households/${HID}/expenses/d1`)))
await no('anonimo NO lee invite_codes', getDoc(doc(anon(), `invite_codes/CODEOWN1`)))
await no('anonimo NO crea hogar', setDoc(doc(anon(), `households/hh_anon`), { createdBy: 'nadie' }))

// ── Documento del hogar y propiedad ───────────────────────────────────────
await ok('MEMBER lee el doc del hogar', getDoc(doc(as('u_member'), `households/${HID}`)))
await no('ajeno NO lee el doc del hogar', getDoc(doc(as('u_ajeno'), `households/${HID}`)))
await ok('OWNER actualiza el hogar', updateDoc(doc(as('u_owner'), `households/${HID}`), { name: 'Renombrado' }))
await no('PAYER NO actualiza el hogar', updateDoc(doc(as('u_payer'), `households/${HID}`), { name: 'x' }))
await no('MEMBER NO actualiza el hogar', updateDoc(doc(as('u_member'), `households/${HID}`), { name: 'x' }))
await no('OWNER de otro hogar NO actualiza este', updateDoc(doc(as('u_ajeno'), `households/${HID}`), { name: 'x' }))
await no('nadie roba un hogar con dueno reescribiendo createdBy', updateDoc(doc(as('u_ajeno'), `households/${HID}`), { createdBy: 'u_ajeno' }))
await no('reclamar un hogar sin dueno a nombre de otro NO', updateDoc(doc(as('u_nuevo'), `households/hh_huerfano`), { createdBy: 'u_otro' }))
await ok('un autenticado reclama un hogar SIN dueno', updateDoc(doc(as('u_nuevo'), `households/hh_huerfano`), { createdBy: 'u_nuevo' }))
await no('un hogar reclamado ya no se reclama otra vez', updateDoc(doc(as('u_nuevo2'), `households/hh_huerfano`), { createdBy: 'u_nuevo2' }))
await ok('crear hogar a mi nombre', setDoc(doc(as('u_fundador'), `households/hh_fundado`), { name: 'Nuevo', createdBy: 'u_fundador' }))
await no('crear hogar a nombre de otro NO', setDoc(doc(as('u_fundador'), `households/hh_fundado2`), { name: 'Nuevo', createdBy: 'u_owner' }))
await ok('el fundador se auto-asigna OWNER en su hogar', setDoc(doc(as('u_fundador'), `households/hh_fundado/roles/u_fundador`), { role: 'OWNER' }))
await no('el fundador NO se auto-asigna OWNER en un hogar ajeno', setDoc(doc(as('u_fundador'), `households/${HID}/roles/u_fundador`), { role: 'OWNER' }))
await ok('OWNER borra el hogar', deleteDoc(doc(as('u_fundador'), `households/hh_fundado`)))
await no('PAYER NO borra el hogar', deleteDoc(doc(as('u_payer'), `households/${HID}`)))

// ── roles/{uid} ───────────────────────────────────────────────────────────
await ok('MEMBER lee su doc de rol', getDoc(doc(as('u_member'), `households/${HID}/roles/u_member`)))
await ok('MEMBER lee el rol de otro miembro', getDoc(doc(as('u_member'), `households/${HID}/roles/u_owner`)))
await no('ajeno NO lee roles', getDoc(doc(as('u_ajeno'), `households/${HID}/roles/u_owner`)))
await no('un extrano NO se auto-asigna OWNER', setDoc(doc(as('u_intruso'), `households/${HID}/roles/u_intruso`), { role: 'OWNER' }))
await no('un extrano NO se auto-asigna PAYER sin invite', setDoc(doc(as('u_intruso'), `households/${HID}/roles/u_intruso`), { role: 'PAYER' }))
await no('canje con invite inexistente NO', setDoc(doc(as('u_intruso'), `households/${HID}/roles/u_intruso`), { role: 'PAYER', inviteCode: 'NOEXISTE' }))
await no('canje con rol distinto al del invite NO', setDoc(doc(as('u_intruso'), `households/${HID}/roles/u_intruso`), { role: 'PAYER', inviteCode: 'INV2MEMB' }))
await no('canje cambiando el miembro nominado NO', setDoc(doc(as('u_intruso'), `households/${HID}/roles/u_intruso`), { role: 'PAYER', inviteCode: 'INV1PAYR', linkedMemberId: 'm-otro' }))
await ok('canje legitimo de invite PAYER nominado', setDoc(doc(as('u_invitado'), `households/${HID}/roles/u_invitado`), { role: 'PAYER', inviteCode: 'INV1PAYR', linkedMemberId: 'm-benjamin' }))
await ok('canje legitimo de invite MEMBER sin nominacion', setDoc(doc(as('u_invitado2'), `households/${HID}/roles/u_invitado2`), { role: 'MEMBER', inviteCode: 'INV2MEMB' }))
await no('nadie crea el doc de rol de OTRO uid', setDoc(doc(as('u_owner'), `households/${HID}/roles/u_colado`), { role: 'MEMBER', inviteCode: 'INV2MEMB' }))
await no('MEMBER NO se sube a OWNER', updateDoc(doc(as('u_member'), `households/${HID}/roles/u_member`), { role: 'OWNER' }))
await no('PAYER NO se sube a OWNER', updateDoc(doc(as('u_payer'), `households/${HID}/roles/u_payer`), { role: 'OWNER' }))
await no('OWNER NO cambia su propio rol', updateDoc(doc(as('u_owner'), `households/${HID}/roles/u_owner`), { role: 'PAYER' }))
await ok('OWNER actualiza su propio doc sin tocar el rol', updateDoc(doc(as('u_owner'), `households/${HID}/roles/u_owner`), { role: 'OWNER', displayName: 'Dueno' }))
await ok('OWNER cambia el rol de otro', updateDoc(doc(as('u_owner'), `households/${HID}/roles/u_member`), { role: 'PAYER' }))
await ok('OWNER lo regresa', updateDoc(doc(as('u_owner'), `households/${HID}/roles/u_member`), { role: 'MEMBER' }))
await no('PAYER NO administra roles ajenos', updateDoc(doc(as('u_payer'), `households/${HID}/roles/u_member`), { role: 'PAYER' }))
await ok('OWNER expulsa a un miembro', deleteDoc(doc(as('u_owner'), `households/${HID}/roles/u_invitado2`)))
await no('PAYER NO expulsa', deleteDoc(doc(as('u_payer'), `households/${HID}/roles/u_member`)))
await no('MEMBER NO se borra a si mismo', deleteDoc(doc(as('u_member'), `households/${HID}/roles/u_member`)))

// ── invites/{code} ────────────────────────────────────────────────────────
await ok('cualquier autenticado lee un invite por codigo', getDoc(doc(as('u_intruso'), `households/${HID}/invites/INV2MEMB`)))
await no('anonimo NO lee un invite', getDoc(doc(anon(), `households/${HID}/invites/INV2MEMB`)))
await no('un autenticado NO enumera invites', getDocs(collection(as('u_intruso'), `households/${HID}/invites`)))
await no('MEMBER NO enumera invites', getDocs(collection(as('u_member'), `households/${HID}/invites`)))
await ok('OWNER enumera invites', getDocs(collection(as('u_owner'), `households/${HID}/invites`)))
await ok('OWNER crea invite', setDoc(doc(as('u_owner'), `households/${HID}/invites/INV3NEW1`), { role: 'MEMBER', uses: 0 }))
await no('PAYER NO crea invite', setDoc(doc(as('u_payer'), `households/${HID}/invites/INV4NEW1`), { role: 'MEMBER', uses: 0 }))
await no('MEMBER NO crea invite', setDoc(doc(as('u_member'), `households/${HID}/invites/INV5NEW1`), { role: 'OWNER', uses: 0 }))
await ok('el canje solo incrementa uses', updateDoc(doc(as('u_intruso'), `households/${HID}/invites/INV2MEMB`), { uses: 1 }))
await no('el canje NO cambia el rol del invite', updateDoc(doc(as('u_intruso'), `households/${HID}/invites/INV2MEMB`), { role: 'OWNER' }))
await no('el canje NO cambia uses y otra clave a la vez', updateDoc(doc(as('u_intruso'), `households/${HID}/invites/INV2MEMB`), { uses: 2, maxUses: 99 }))
await ok('OWNER borra invite', deleteDoc(doc(as('u_owner'), `households/${HID}/invites/INV3NEW1`)))
await no('MEMBER NO borra invite', deleteDoc(doc(as('u_member'), `households/${HID}/invites/INV2MEMB`)))

// ── proposals/{id} ────────────────────────────────────────────────────────
await ok('MEMBER lee proposals', getDoc(doc(as('u_member'), `households/${HID}/proposals/d1`)))
await ok('MEMBER lista proposals', getDocs(collection(as('u_member'), `households/${HID}/proposals`)))
await ok('MEMBER crea proposal', setDoc(doc(as('u_member'), `households/${HID}/proposals/p1`), { x: 1 }))
await ok('OWNER crea proposal', setDoc(doc(as('u_owner'), `households/${HID}/proposals/p2`), { x: 1 }))
await no('ajeno NO crea proposal', setDoc(doc(as('u_ajeno'), `households/${HID}/proposals/p3`), { x: 1 }))
await no('ajeno NO lee proposals', getDoc(doc(as('u_ajeno'), `households/${HID}/proposals/d1`)))
await no('MEMBER NO resuelve ni edita su propuesta', updateDoc(doc(as('u_member'), `households/${HID}/proposals/p_member`), { status: 'ACCEPTED' }))
await no('MEMBER NO borra su propuesta', deleteDoc(doc(as('u_member'), `households/${HID}/proposals/p_member`)))
await ok('PAYER resuelve una propuesta', updateDoc(doc(as('u_payer'), `households/${HID}/proposals/p_member`), { status: 'ACCEPTED' }))
await ok('OWNER borra una propuesta', deleteDoc(doc(as('u_owner'), `households/${HID}/proposals/p_member`)))

// ── users/{uid}/** ────────────────────────────────────────────────────────
await ok('el usuario lee su perfil', getDoc(doc(as('u_member'), `users/u_member`)))
await ok('el usuario escribe su perfil', updateDoc(doc(as('u_member'), `users/u_member`), { activeHouseholdId: HID }))
await ok('el usuario lee su espejo de hogares', getDoc(doc(as('u_member'), `users/u_member/households/${HID}`)))
await no('otro usuario NO lee un perfil ajeno', getDoc(doc(as('u_owner'), `users/u_member`)))
await no('otro usuario NO escribe un perfil ajeno', updateDoc(doc(as('u_owner'), `users/u_member`), { activeHouseholdId: 'x' }))
await no('otro usuario NO lee el espejo ajeno', getDoc(doc(as('u_owner'), `users/u_member/households/${HID}`)))
await no('anonimo NO lee perfiles', getDoc(doc(anon(), `users/u_member`)))

// ── invite_codes/{code} (indice global opaco) ─────────────────────────────
await ok('cualquier autenticado canjea por codigo (get)', getDoc(doc(as('u_intruso'), `invite_codes/CODEOWN1`)))
await no('nadie enumera invite_codes', getDocs(collection(as('u_owner'), `invite_codes`)))
await ok('OWNER crea un codigo de su hogar', setDoc(doc(as('u_owner'), `invite_codes/CODEOWN2`), { householdId: HID, role: 'MEMBER', uses: 0 }))
await no('PAYER NO crea un codigo', setDoc(doc(as('u_payer'), `invite_codes/CODEPAY1`), { householdId: HID, role: 'MEMBER', uses: 0 }))
await no('OWNER NO crea un codigo para otro hogar', setDoc(doc(as('u_owner'), `invite_codes/CODEOWN3`), { householdId: OTHER_HID, role: 'MEMBER', uses: 0 }))
await ok('el canje incrementa uses', updateDoc(doc(as('u_intruso'), `invite_codes/CODEOWN1`), { uses: 1 }))
await no('el canje NO cambia el hogar del codigo', updateDoc(doc(as('u_intruso'), `invite_codes/CODEOWN1`), { householdId: OTHER_HID }))
await no('el canje NO cambia el rol del codigo', updateDoc(doc(as('u_intruso'), `invite_codes/CODEOWN1`), { uses: 2, role: 'OWNER' }))
await ok('OWNER borra un codigo de su hogar', deleteDoc(doc(as('u_owner'), `invite_codes/CODEOWN2`)))
await no('OWNER NO borra el codigo de otro hogar', deleteDoc(doc(as('u_owner'), `invite_codes/CODEAJN1`)))

// ── Sin comodin final: lo no declarado nace cerrado ───────────────────────
await no('coleccion no declarada cerrada (OWNER)', getDoc(doc(as('u_owner'), `households/${HID}/coleccion_nueva/d1`)))
await no('coleccion no declarada cerrada para escribir (OWNER)', setDoc(doc(as('u_owner'), `households/${HID}/coleccion_nueva/d1`), { x: 1 }))
await no('raiz desconocida cerrada', getDoc(doc(as('u_owner'), `otra_raiz/d1`)))

await env.cleanup()
console.log(`${pass}/${pass + fail} casos en verde`)
process.exit(fail ? 1 : 0)
