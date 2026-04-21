# 📋 Cuestionario de Clasificación de Gastos

> **Instrucciones**: Revisa cada concepto. Si la clasificación ETL es correcta, no hagas nada.
> Si es incorrecta, completa las columnas `Cat. Correcta`, `Beneficiarios Correctos` y `Pago Correcto`.
> Usa `✅` para confirmar o escribe el valor correcto.
> Los valores que dejes en blanco se tomarán como **correctos tal cual**.

## Miembros del hogar
| Clave | Nombre | Rol |
|-------|--------|-----|
| `ben` | Benjamín | Adulto-pagador |
| `nor` | Norma | Adulto-pagadora |
| `pau` | Pau | Dependiente |
| `dav` | David | Dependiente |
| `agu` | Agustín | Dependiente |
| `san` | Santiago | Dependiente |
| `todos` | Todos los anteriores | — |

## Categorías disponibles
*(Si necesitas crear una nueva, escríbela como `NUEVA: nombre`)*

| Código | Descripción |
|--------|-------------|
| `HOUSING` | Vivienda (raíz) |
| `HOUSING.HIPOTECA` | Hipoteca |
| `HOUSING.INTERNET` | Internet |
| `HOUSING.ELECTRICIDAD` | Electricidad |
| `HOUSING.AGUA` | Agua |
| `HOUSING.TELEFONO` | Teléfono |
| `HOUSING.FRACCIONAMIENTO` | Fraccionamiento |
| `TRANSPORTATION` | Transporte (raíz) |
| `TRANSPORTATION.GASOLINA` | Gasolina |
| `TRANSPORTATION.INSURANCE` | Seguro vehículo |
| `TRANSPORTATION.MAINTENANCE` | Mantenimiento |
| `SEGUROS_MEDICOS` | Seguros Médicos (raíz) |
| `SEGUROS_MEDICOS.BENJI` | Seguro Benji |
| `SEGUROS_MEDICOS.NORMA` | Seguro Norma |
| `SEGUROS_MEDICOS.HIJOS` | Seguro hijos (Pau+David+Agus) |
| `SEGUROS_MEDICOS.SANTI` | Seguro Santi |
| `FOOD` | Alimentación (raíz) |
| `FOOD.COMIDA` | Comida |
| `FOOD.DESPENSA` | Despensa |
| `FOOD.LIMPIEZA` | Limpieza |
| `PETS` | Mascotas (raíz) |
| `PETS.COMIDA` | Comida gatas |
| `PETS.GROOMING` | Grooming |
| `PETS.VETERINARIO` | Veterinario |
| `ENTERTAINMENT` | Entretenimiento (raíz) |
| `ENTERTAINMENT.NETFLIX` | Netflix |
| `ENTERTAINMENT.HBO` | HBO |
| `ENTERTAINMENT.PRIME` | Amazon Prime |
| `ENTERTAINMENT.DISNEY` | Disney+ |
| `ENTERTAINMENT.SPOTIFY` | Spotify |
| `ENTERTAINMENT.HAWAIANO` | Hawaiano |
| `ENTERTAINMENT.DIVERSION` | Diversión |
| `LOANS` | Préstamos/Tarjetas (raíz) |
| `LOANS.COPPEL` | Coppel |
| `LOANS.LIVERPOOL` | Liverpool |
| `LOANS.SEARS` | Sears |
| `LOANS.WALMART` | Walmart |
| `LOANS.BANAMEX_CC` | Banamex Clásica |
| `LOANS.MERCADO_LIBRE` | Mercado Libre |
| `LOANS.MERCADO_PAGO` | Mercado Pago |
| `LOANS.OMAR` | Préstamo Omar |
| `TRANSFERENCIAS_FAMILIARES` | Transferencias Familiares (raíz) |
| `TRANSFERENCIAS_FAMILIARES.DAVID` | Transferencia David |
| `TRANSFERENCIAS_FAMILIARES.PAU` | Transferencia Pau |
| `TRANSFERENCIAS_FAMILIARES.SANTIAGO` | Transferencia Santi |
| `TRANSFERENCIAS_FAMILIARES.COCHE` | Coche |
| `TRANSFERENCIAS_FAMILIARES.INSCRIPCIONES` | Inscripciones |
| `SAVINGS` | Ahorros (raíz) |
| `SAVINGS.EMPRESA` | Ahorro Empresa |
| `SAVINGS.TARJETA` | Tarjeta de ahorro |
| `SAVINGS.RETIREMENT` | Retiro |
| `SAVINGS.INVESTMENT` | Inversión |
| `SAVINGS.EFECTIVO` | Ahorro efectivo |
| `PERSONAL_CARE` | Cuidado Personal |
| `GIFTS` | Regalos y Donaciones |
| `LEGAL` | Legal |
| `OTHER` | Otros |
| `SERVICIOS_EXTERNOS.ARACELI` | Araceli (empleada del hogar) |
| `SERVICIOS_EXTERNOS.PSICOLOGA` | Psicóloga |

---

## 🏠 Vivienda (HOUSING)

| N° | Concepto | Variantes encontradas | Quincenas | Monto (min–max) | Quién paga | **Cat. ETL actual** | **Benef. ETL actual** | **Pago ETL actual** | ✏️ Cat. Correcta | ✏️ Beneficiarios Correctos | ✏️ Metodo Pago Correcto |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | **Hipoteca** | — | 33 | $3,750–$7,000 | Norma | `HOUSING.HIPOTECA` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 2 | **Agua** | — | 17 | $203–$1,070 | Norma | `HOUSING.AGUA` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 3 | **Electricidad** | — | 17 | $1,000–$2,693 | Norma | `HOUSING.ELECTRICIDAD` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 4 | **Internet** | — | 17 | $899 | Norma | `HOUSING.INTERNET` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 5 | **Telefono Norma** | — | 15 | $498 | Norma | `HOUSING.TELEFONO` | Norma | BBVA (Norma) |  |  |  |
| 6 | **Telefono Pau y David** | — | 15 | $444 | Norma | `HOUSING.TELEFONO` | Pau, David | BBVA (Norma) |  |  |  |
| 7 | **Telefono Santi** | — | 15 | $199 | Norma | `HOUSING.TELEFONO` | Santiago | BBVA (Norma) |  |  |  |
| 8 | **Telefono Benji** | — | 8 | $760 | Benji | `HOUSING.TELEFONO` | Benjamín | Efectivo (Benji) |  |  |  |
| 9 | **David** | — | 1 | $249 | Norma | `HOUSING` | David | BBVA (Norma) |  |  |  |
| 10 | **Telefono Movistar** | — | 1 | $894 | Norma | `HOUSING.TELEFONO` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |

## 🚗 Transporte (TRANSPORTATION)

| N° | Concepto | Variantes encontradas | Quincenas | Monto (min–max) | Quién paga | **Cat. ETL actual** | **Benef. ETL actual** | **Pago ETL actual** | ✏️ Cat. Correcta | ✏️ Beneficiarios Correctos | ✏️ Metodo Pago Correcto |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 11 | **Gasolina Camioneta** | — | 17 | $1,240–$2,600 | Norma | `TRANSPORTATION.GASOLINA` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 12 | **Gasolina Chochecito** | — | 17 | $1,400–$1,600 | Norma | `TRANSPORTATION.GASOLINA` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 13 | **Cochecito** | — | 16 | $1,400–$1,600 | Norma | `TRANSPORTATION` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 14 | **Gasolina** | — | 16 | $1,000–$2,600 | Norma | `TRANSPORTATION.GASOLINA` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |

## 🏥 Seguros Médicos (PERSONAL)

| N° | Concepto | Variantes encontradas | Quincenas | Monto (min–max) | Quién paga | **Cat. ETL actual** | **Benef. ETL actual** | **Pago ETL actual** | ✏️ Cat. Correcta | ✏️ Beneficiarios Correctos | ✏️ Metodo Pago Correcto |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 15 | **Norma** | — | 33 | $1,703–$2,500 | Ambos | `SEGUROS_MEDICOS.NORMA` | Norma | BBVA (Norma) |  |  |  |
| 16 | **Pau, David, Agus** | — | 33 | $3,000–$3,500 | Norma | `SEGUROS_MEDICOS.HIJOS` | Pau, David, Agustín | BBVA (Norma) |  |  |  |
| 17 | **Santi** | — | 30 | $329–$500 | Ambos | `SEGUROS_MEDICOS.SANTI` | Santiago | BBVA (Norma) |  |  |  |
| 18 | **Benji** | — | 9 | $2,500 | Benji | `SEGUROS_MEDICOS.BENJI` | Benjamín | Efectivo (Benji) |  |  |  |

## 🛒 Alimentación (FOOD)

| N° | Concepto | Variantes encontradas | Quincenas | Monto (min–max) | Quién paga | **Cat. ETL actual** | **Benef. ETL actual** | **Pago ETL actual** | ✏️ Cat. Correcta | ✏️ Beneficiarios Correctos | ✏️ Metodo Pago Correcto |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 19 | **Comida** | — | 33 | $3,000–$5,250 | Ambos | `FOOD.COMIDA` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 20 | **Despensa** | — | 33 | $1,500–$2,500 | Norma | `FOOD.DESPENSA` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 21 | **Limpieza** | — | 33 | $2,000–$5,100 | Norma | `FOOD.LIMPIEZA` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |

## 🐱 Mascotas (PETS)

| N° | Concepto | Variantes encontradas | Quincenas | Monto (min–max) | Quién paga | **Cat. ETL actual** | **Benef. ETL actual** | **Pago ETL actual** | ✏️ Cat. Correcta | ✏️ Beneficiarios Correctos | ✏️ Metodo Pago Correcto |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 22 | **Comida Gatas** | — | 33 | $980 | Norma | `PETS.COMIDA` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 23 | **Psicologa** | — | 1 | $900 | Norma | `PETS` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |

## 🎬 Entretenimiento (ENTERTAINMENT)

| N° | Concepto | Variantes encontradas | Quincenas | Monto (min–max) | Quién paga | **Cat. ETL actual** | **Benef. ETL actual** | **Pago ETL actual** | ✏️ Cat. Correcta | ✏️ Beneficiarios Correctos | ✏️ Metodo Pago Correcto |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 24 | **Diversion** | `Diversión` | 20 | $1,400–$4,500 | Norma | `ENTERTAINMENT.DIVERSION` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 25 | **HBO** | — | 17 | $85–$167 | Norma | `ENTERTAINMENT.HBO` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 26 | **Suscripcion nivel 6** | — | 17 | $129–$130 | Norma | `ENTERTAINMENT` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 27 | **Netflix** | — | 16 | $329 | Norma | `ENTERTAINMENT.NETFLIX` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 28 | **Prime** | — | 16 | $99 | Norma | `ENTERTAINMENT.PRIME` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 29 | **Adobe** | — | 9 | $249 | Norma | `ENTERTAINMENT` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 30 | **Google** | — | 8 | $39 | Norma | `ENTERTAINMENT` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 31 | **Google nest** | — | 8 | $200 | Norma | `ENTERTAINMENT` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 32 | **Hawaiano** | — | 8 | $350–$450 | Ambos | `ENTERTAINMENT.HAWAIANO` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 33 | **KIGO** | — | 8 | $100 | Ambos | `ENTERTAINMENT` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 34 | **Spotify** | — | 8 | $179 | Benji | `ENTERTAINMENT.SPOTIFY` | Norma, Benjamín (default) | Efectivo (Benji) |  |  |  |
| 35 | **Coursera** | — | 7 | $288 | Norma | `ENTERTAINMENT` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 36 | **Didi tarjeta** | — | 6 | $4,840–$6,500 | Norma | `ENTERTAINMENT` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 37 | **Youtube** | — | 5 | $179 | Ambos | `ENTERTAINMENT` | Norma, Benjamín (default) | Efectivo (Benji) |  |  |  |

## 💳 Préstamos y Tarjetas (LOANS)

| N° | Concepto | Variantes encontradas | Quincenas | Monto (min–max) | Quién paga | **Cat. ETL actual** | **Benef. ETL actual** | **Pago ETL actual** | ✏️ Cat. Correcta | ✏️ Beneficiarios Correctos | ✏️ Metodo Pago Correcto |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 38 | **Didi** | — | 18 | $778–$1,049 | Ambos | `LOANS` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 39 | **Liverpool** | — | 18 | $2,000–$3,258 | Ambos | `LOANS.LIVERPOOL` | Norma, Benjamín (default) | Liverpool |  |  |  |
| 40 | **Banamex Clasica** | — | 16 | $1,500 | Norma | `LOANS.BANAMEX_CC` | Norma, Benjamín (default) | Banamex Clásica |  |  |  |
| 41 | **Sears** | — | 16 | $1,000–$1,920 | Ambos | `LOANS.SEARS` | Norma, Benjamín (default) | Sears |  |  |  |
| 42 | **Walmart** | — | 16 | $7,500 | Norma | `LOANS.WALMART` | Norma, Benjamín (default) | Walmart |  |  |  |
| 43 | **mercado pago** | — | 10 | $355–$5,546 | Ambos | `LOANS.MERCADO_PAGO` | Norma, Benjamín (default) | Mercado Pago |  |  |  |
| 44 | **Coppel** | — | 8 | $380–$3,660 | Norma | `LOANS.COPPEL` | Norma, Benjamín (default) | Coppel |  |  |  |
| 45 | **Mercado libre 1 de 12** | — | 5 | $1,342–$2,710 | Norma | `LOANS.MERCADO_LIBRE` | Norma, Benjamín (default) | Mercado Libre |  |  |  |
| 46 | **Mary** | — | 3 | $2,404 | Sin dato | `LOANS` | Norma, Benjamín (default) | Efectivo |  |  |  |
| 47 | **Mercado libre 7 de 12** | — | 3 | $1,342 | Norma | `LOANS.MERCADO_LIBRE` | Norma, Benjamín (default) | Mercado Libre |  |  |  |
| 48 | **Buro de credito 2 de 3** | — | 1 | $4,300 | Norma | `LOANS` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 49 | **Didi Card** | — | 1 | $2,200 | Norma | `LOANS` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 50 | **Hawainano** | — | 1 | $350 | Norma | `LOANS` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 51 | **Prestamo Omar 10** | — | 1 | $5,500 | Norma | `LOANS.OMAR` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 52 | **Prestamo Omar 3** | — | 1 | $5,500 | Norma | `LOANS.OMAR` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 53 | **Prestamo Omar 4** | — | 1 | $5,500 | Norma | `LOANS.OMAR` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 54 | **Prestamo Omar 5** | — | 1 | $5,500 | Norma | `LOANS.OMAR` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 55 | **Prestamo Omar 6** | — | 1 | $5,500 | Norma | `LOANS.OMAR` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 56 | **Prestamo Omar 7** | — | 1 | $5,500 | Norma | `LOANS.OMAR` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 57 | **Prestamo Omar 8** | — | 1 | $5,500 | Norma | `LOANS.OMAR` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 58 | **Prestamo Omar 9** | — | 1 | $5,500 | Norma | `LOANS.OMAR` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |

## 👨‍👩‍👧‍👦 Transferencias Familiares (SCHOOL/TAXES)

| N° | Concepto | Variantes encontradas | Quincenas | Monto (min–max) | Quién paga | **Cat. ETL actual** | **Benef. ETL actual** | **Pago ETL actual** | ✏️ Cat. Correcta | ✏️ Beneficiarios Correctos | ✏️ Metodo Pago Correcto |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 59 | **David** | — | 14 | $8,000–$19,000 | Norma | `TRANSFERENCIAS_FAMILIARES.DAVID` | David | BBVA (Norma) |  |  |  |
| 60 | **Pau** | — | 13 | $800–$10,000 | Ambos | `TRANSFERENCIAS_FAMILIARES.PAU` | Pau | BBVA (Norma) |  |  |  |
| 61 | **Santiago** | — | 6 | $6,600–$7,000 | Norma | `TRANSFERENCIAS_FAMILIARES.SANTIAGO` | Santiago | BBVA (Norma) |  |  |  |
| 62 | **Inscripcion Santi** | — | 2 | $2,000–$5,000 | Norma | `TRANSFERENCIAS_FAMILIARES.SANTIAGO` | Santiago | BBVA (Norma) |  |  |  |
| 63 | **Santiago Abril** | — | 2 | $3,000–$4,300 | Norma | `TRANSFERENCIAS_FAMILIARES.SANTIAGO` | Santiago | BBVA (Norma) |  |  |  |
| 64 | **Santiago Febrero** | — | 2 | $2,000–$5,000 | Norma | `TRANSFERENCIAS_FAMILIARES.SANTIAGO` | Santiago | BBVA (Norma) |  |  |  |
| 65 | **Santiago Marzo** | — | 2 | $2,200–$5,100 | Norma | `TRANSFERENCIAS_FAMILIARES.SANTIAGO` | Santiago | BBVA (Norma) |  |  |  |
| 66 | **David Abril** | — | 1 | $15,000 | Norma | `TRANSFERENCIAS_FAMILIARES.DAVID` | David | BBVA (Norma) |  |  |  |
| 67 | **David Febrero** | — | 1 | $9,000 | Norma | `TRANSFERENCIAS_FAMILIARES.DAVID` | David | BBVA (Norma) |  |  |  |
| 68 | **David Febrero y 3000 Marzo** | — | 1 | $8,000 | Norma | `TRANSFERENCIAS_FAMILIARES.DAVID` | David | BBVA (Norma) |  |  |  |
| 69 | **David marzo** | — | 1 | $12,000 | Norma | `TRANSFERENCIAS_FAMILIARES.DAVID` | David | BBVA (Norma) |  |  |  |
| 70 | **David Mayo** | — | 1 | $15,000 | Norma | `TRANSFERENCIAS_FAMILIARES.DAVID` | David | BBVA (Norma) |  |  |  |
| 71 | **Santiago Inscripcion** | — | 1 | $8,800 | Norma | `TRANSFERENCIAS_FAMILIARES.SANTIAGO` | Santiago | BBVA (Norma) |  |  |  |
| 72 | **Santiago Mayo** | — | 1 | $7,000 | Norma | `TRANSFERENCIAS_FAMILIARES.SANTIAGO` | Santiago | BBVA (Norma) |  |  |  |

## 📦 Otros (OTHERS)

| N° | Concepto | Variantes encontradas | Quincenas | Monto (min–max) | Quién paga | **Cat. ETL actual** | **Benef. ETL actual** | **Pago ETL actual** | ✏️ Cat. Correcta | ✏️ Beneficiarios Correctos | ✏️ Metodo Pago Correcto |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 73 | **Psicologa** | — | 19 | $900 | Norma | `OTHER` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 74 | **Buro de credito 1 de 3** | — | 1 | $4,300 | Norma | `OTHER` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 75 | **Hawaiano** | — | 1 | $350 | Norma | `OTHER` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 76 | **Inscripcion Santi** | — | 1 | $9,600 | Norma | `OTHER` | Santiago | BBVA (Norma) |  |  |  |
| 77 | **Pau** | — | 1 | $800 | Norma | `OTHER` | Pau | BBVA (Norma) |  |  |  |
| 78 | **Prestamo 1 Omar** | — | 1 | $5,500 | Norma | `OTHER` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |
| 79 | **Prestamo 2 (Omar)** | — | 1 | $5,500 | Norma | `OTHER` | Norma, Benjamín (default) | BBVA (Norma) |  |  |  |

---

## Resumen

- **Total conceptos únicos encontrados**: 79
- **Secciones analizadas**: 9
- **Hojas procesadas**: 79