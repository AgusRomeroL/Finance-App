# 📋 Cuestionario de Clasificación de Gastos

> \*\*Instrucciones\*\*: Revisa cada concepto. Si la clasificación ETL es \*\*correcta\*\*, deja las columnas ✏️ vacías.
> Si es \*\*incorrecta\*\*, escribe el valor correcto en la columna correspondiente.
> Los conceptos con el mismo nombre en diferentes quincenas se procesan con UNA SOLA regla.

## Claves de miembros

|Clave|Nombre|
|-|-|
|`ben`|Benjamín|
|`nor`|Norma|
|`pau`|Pau|
|`dav`|David|
|`agu`|Agustín|
|`san`|Santiago|
|`todos`|Todos (ben, nor, pau, dav, agu, san)|
|`adultos`|Solo adultos (ben, nor)|
|`hijos`|Solo dependientes (pau, dav, agu, san)|

\---

## 🏠 Vivienda (HOUSING)

|N°|Concepto|Quincenas|Monto|Quién paga|Cat. ETL actual|Benef. ETL actual|✏️ Cat. Correcta|✏️ Beneficiarios Correctos|✏️ Método Pago|
|-|-|-|-|-|-|-|-|-|-|
|1|**Hipoteca**|33|$3,750–$7,000|Norma|`HOUSING.HIPOTECA`|nor, ben (default)|`HOUSING.HIPOTECA`|adultos||
|2|**Agua**|17|$203–$1,070|Norma|`HOUSING.AGUA`|nor, ben (default)|`HOUSING.AGUA`|todos|efectivo|
|3|**Electricidad**|17|$1,000–$2,693|Norma|`HOUSING.ELECTRICIDAD`|nor, ben (default)|`HOUSING.ELECTRICIDAD`|todos|efectivo|
|4|**Internet**|17|$899|Norma|`HOUSING.INTERNET`|nor, ben (default)|`HOUSING.INTERNET`|todos|efectivo|
|5|**Telefono Norma**|15|$498|Norma|`HOUSING.TELEFONO`|nor|`OTHER.TELEFONO`|nor|efectivo|
|6|**Telefono Pau y David**|15|$444|Norma|`HOUSING.TELEFONO`|pau, dav|`OTHER.TELEFONO`|pau, dav|efectivo|
|7|**Telefono Santi**|15|$199|Norma|`HOUSING.TELEFONO`|san|`OTHER.TELEFONO`|san|efectivo|
|8|**Telefono Benji**|8|$760|Benji|`HOUSING.TELEFONO`|ben|`OTHER.TELEFONO`|ben|efectivo|
|9|**David** *(en sección HOUSING — ¿teléfono?)*|1|$249|Norma|`HOUSING`|dav|`OTHER.TELEFONO`|dav|efectivo|
|10|**Telefono Movistar**|1|$894|Norma|`HOUSING.TELEFONO`|nor, ben (default)|`OTHER.TELEFONO`|dav, nor, pau, san|efectivo|

\---

## 🚗 Transporte (TRANSPORTATION)

|N°|Concepto|Quincenas|Monto|Quién paga|Cat. ETL actual|Benef. ETL actual|✏️ Cat. Correcta|✏️ Beneficiarios Correctos|✏️ Método Pago|
|-|-|-|-|-|-|-|-|-|-|
|11|**Gasolina Camioneta**|17|$1,240–$2,600|Norma|`TRANSPORTATION.GASOLINA`|nor, ben (default)|`TRANSPORTATION.GASOLINA`|nor|efectivo|
|12|**Gasolina Chochecito**|17|$1,400–$1,600|Norma|`TRANSPORTATION.GASOLINA`|nor, ben (default)|`TRANSPORTATION.GASOLINA`|agu, dav, pau|efectivo|
|13|**Cochecito** *(¿es la misma gasolina con nombre distinto?)<br /><br />R=Si es la misma "Gasolina Chochecito"*|16|$1,400–$1,600|Norma|`TRANSPORTATION`|nor, ben (default)|`TRANSPORTATION.GASOLINA`|agu, dav, pau|efectivo|
|14|**Gasolina** *(genérica — ¿camioneta o cochecito?)<br /><br />R=Es "Gasolina Camioneta"*|16|$1,000–$2,600|Norma|`TRANSPORTATION.GASOLINA`|nor, ben (default)|`TRANSPORTATION.GASOLINA`|nor|efectivo|

\---

## 🏥 Seguros Médicos (PERSONAL)

|N°|Concepto|Quincenas|Monto|Quién paga|Cat. ETL actual|Benef. ETL actual|✏️ Cat. Correcta|✏️ Beneficiarios Correctos|✏️ Método Pago|
|-|-|-|-|-|-|-|-|-|-|
|15|**Norma**|33|$1,703–$2,500|Ambos|`SEGUROS\_MEDICOS.NORMA`|nor||||
|16|**Pau, David, Agus**|33|$3,000–$3,500|Norma|`SEGUROS\_MEDICOS.HIJOS`|pau, dav, agu||||
|17|**Santi**|30|$329–$500|Ambos|`SEGUROS\_MEDICOS.SANTI`|san||||
|18|**Benji**|9|$2,500|Benji|`SEGUROS\_MEDICOS.BENJI`|ben||||

> ⚠️ \*\*Nota\*\*: "Seguro hijos" aparecía en el plan anterior como mal clasificado. Aquí el ETL ya detectó correctamente "Pau, David, Agus" → `SEGUROS\_MEDICOS.HIJOS`. ¿Es correcto o debe ser `TRANSFERENCIAS\_FAMILIARES` (mesada)?

R= debe ser `TRANSFERENCIAS\_FAMILIARES.NOMBRE`, a excepción del 18, de Benji, ese es el único de Seguros Médicos. En este caso, de las 33 quincenas, serían 3 mesadas, `TRANSFERENCIAS\_FAMILIARES.PAU`, `TRANSFERENCIAS\_FAMILIARES.DAVID` y  `TRANSFERENCIAS\_FAMILIARES.AGUSTÍN`, cada una por $1,000, en la que hubo $500 seguramente hubo `TRANSFERENCIAS\_FAMILIARES.SANTIAGO` por $500. Para Norma, es `TRANSFERENCIAS\_FAMILIARES.NORMA`

\---

## 🛒 Alimentación (FOOD)

|N°|Concepto|Quincenas|Monto|Quién paga|Cat. ETL actual|Benef. ETL actual|✏️ Cat. Correcta|✏️ Beneficiarios Correctos|✏️ Método Pago|
|-|-|-|-|-|-|-|-|-|-|
|19|**Comida**|33|$3,000–$5,250|Ambos|`FOOD.COMIDA`|nor, ben (default)|`FOOD.COMIDA`|todos|efectivo|
|20|**Despensa**|33|$1,500–$2,500|Norma|`FOOD.DESPENSA`|nor, ben (default)|`FOOD.DESPENSA`|todos|efectivo|
|21|**Limpieza**|33|$2,000–$5,100|Norma|`FOOD.LIMPIEZA`|nor, ben (default)|`FOOD.LIMPIEZA`|todos|efectivo|

\---

## 🐱 Mascotas (PETS)

|N°|Concepto|Quincenas|Monto|Quién paga|Cat. ETL actual|Benef. ETL actual|✏️ Cat. Correcta|✏️ Beneficiarios Correctos|✏️ Método Pago|
|-|-|-|-|-|-|-|-|-|-|
|22|**Comida Gatas**|33|$980|Norma|`PETS.COMIDA`|nor, ben (default)|`PETS.COMIDA`|todos|efectivo|
|23|**Psicologa** *(en sección PETS — ¿error de hoja?)*|1|$900|Norma|`PETS` ❌|nor, ben (default)|`OTHER.SALUD`|nor|efectivo|

\---

## 🎬 Entretenimiento (ENTERTAINMENT)

|N°|Concepto|Quincenas|Monto|Quién paga|Cat. ETL actual|Benef. ETL actual|✏️ Cat. Correcta|✏️ Beneficiarios Correctos|✏️ Método Pago|
|-|-|-|-|-|-|-|-|-|-|
|24|**Diversion**|20|$1,400–$4,500|Norma|`ENTERTAINMENT.DIVERSION`|nor, ben (default)|`ENTERTAINMENT.DIVERSION`|todos|efectivo|
|25|**HBO**|17|$85–$167|Norma|`ENTERTAINMENT.HBO`|nor, ben (default)|`ENTERTAINMENT.HBO`|todos|efectivo|
|26|**Suscripcion nivel 6** *(¿qué es esto? ¿Duolingo? ¿otro?)<br /><br />R= Meli+ de Mercado Libre*|17|$129–$130|Norma|`ENTERTAINMENT` ❓|nor, ben (default)|`ENTERTAINMENT.MELI+`|todos|efectivo|
|27|**Netflix**|16|$329|Norma|`ENTERTAINMENT.NETFLIX`|nor, ben (default)|`ENTERTAINMENT.NETFLIX`|todos|efectivo|
|28|**Prime**|16|$99|Norma|`ENTERTAINMENT.PRIME`|nor, ben (default)|`ENTERTAINMENT.PRIME`|todos|efectivo|
|29|**Adobe**|9|$249|Norma|`ENTERTAINMENT` ❓|nor, ben (default)|`OTHER.ADOBE`|nor|efectivo|
|30|**Google** *(¿Google One storage?)<br /><br />R=Efectivamente Google One*|8|$39|Norma|`ENTERTAINMENT` ❓|nor, ben (default)|`OTHER.GOOGLE ONE`|nor|efectivo|
|31|**Google nest**|8|$200|Norma|`ENTERTAINMENT` ❓|nor, ben (default)|`OTHER.GOOGLE HOME`|nor|efectivo|
|32|**Hawaiano**|8|$350–$450|Ambos|`ENTERTAINMENT.HAWAIANO`|nor, ben (default)|`OTHER.HAWAIANO`|pau|efectivo|
|33|**KIGO** *(¿app kids? ¿otro servicio?)*|8|$100|Ambos|`ENTERTAINMENT` ❓|nor, ben (default)|`OTHER.KIGO`|nor|efectivo|
|34|**Spotify**|8|$179|Benji|`ENTERTAINMENT.SPOTIFY`|nor, ben (default)|`ENTERTAINMENT.SPOTIFY`|ben|efectivo|
|35|**Coursera**|7|$288|Norma|`ENTERTAINMENT` ❓|nor, ben (default)|`OTHER.SPOTIFY`|nor|efectivo|
|36|**Didi tarjeta** *(¿pago de tarjeta Didi?)<br /><br />R=Si, tarjeta de crédito*|6|$4,840–$6,500|Norma|`ENTERTAINMENT` ❌|nor, ben (default)|`LOANS.DIDI`|nor|efectivo|
|37|**Youtube**|5|$179|Ambos|`ENTERTAINMENT`|nor, ben (default)|`ENTERTAINMENT.YOUTUBE PREMIUM`|todos|efectivo|

\---

## 💳 Préstamos y Tarjetas (LOANS)

|N°|Concepto|Quincenas|Monto|Quién paga|Cat. ETL actual|Benef. ETL actual|✏️ Cat. Correcta|✏️ Beneficiarios Correctos|✏️ Método Pago|
|-|-|-|-|-|-|-|-|-|-|
|38|**Didi** *(¿cuota de Didi Card?)*|18|$778–$1,049|Ambos|`LOANS`|nor, ben (default)|`LOANS.DIDI`|nor|efectivo|
|39|**Liverpool**|18|$2,000–$3,258|Ambos|`LOANS.LIVERPOOL`|nor, ben (default)|`LOANS.LIVERPOOL`|nor|efectivo|
|40|**Banamex Clasica**|16|$1,500|Norma|`LOANS.BANAMEX\_CC`|nor, ben (default)|`LOANS.BANAMEX\_CC`|nor|efectivo|
|41|**Sears**|16|$1,000–$1,920|Ambos|`LOANS.SEARS`|nor, ben (default)|`LOANS.SEARS`|nor|efectivo|
|42|**Walmart**|16|$7,500|Norma|`LOANS.WALMART`|nor, ben (default)|`LOANS.WALMART`|nor|efectivo|
|43|**mercado pago**|10|$355–$5,546|Ambos|`LOANS.MERCADO\_PAGO`|nor, ben (default)|`LOANS.MERCADO\_PAGO`|nor|efectivo|
|44|**Coppel**|8|$380–$3,660|Norma|`LOANS.COPPEL`|nor, ben (default)|`LOANS.COPPEL`|nor|efectivo|
|45|**Mercado libre 1 de 12**|5|$1,342–$2,710|Norma|`LOANS.MERCADO\_LIBRE`|nor, ben (default)|`LOANS.MERCADO\_LIBRE`|nor|efectivo|
|46|**Mary** *(¿quién es Mary? ¿servicio externo?)<br /><br />R=Una persona que conoce Norma*|3|$2,404|Sin dato|`LOANS` ❓|nor, ben (default)|`OTHER.MARY`|nor|efectivo|
|47|**Mercado libre 7 de 12**|3|$1,342|Norma|`LOANS.MERCADO\_LIBRE`|nor, ben (default)|`LOANS.MERCADO\_LIBRE`|nor|efectivo|
|48|**Buro de credito 2 de 3**|1|$4,300|Norma|`LOANS`|nor, ben (default)|`LOANS.BURO`|nor|efectivo|
|49|**Didi Card**|1|$2,200|Norma|`LOANS`|nor, ben (default)|`LOANS.DIDI`|nor|efectivo|
|50|**Hawainano** *(typo de Hawaiano en sección LOANS)<br /><br />R=No es Loan, son clases de hawaiano*|1|$350|Norma|`LOANS` ❌|nor, ben (default)|`OTHER.HAWAIANO`|pau|efectivo|
|51–58|**Prestamo Omar 3 al 10**|1 c/u|$5,500|Norma|`LOANS.OMAR`|nor, ben (default)|`LOANS.OMAR`|nor|efectivo|

> ⚠️ \*\*Nota\*\*: Los "Prestamo Omar X" deberían consolidarse en un `InstallmentPlan`. Son la misma cuota repetida en diferentes quincenas.

\---

## 👨‍👩‍👧‍👦 Transferencias Familiares (SCHOOL/TAXES)

|N°|Concepto|Quincenas|Monto|Quién paga|Cat. ETL actual|Benef. ETL actual|✏️ Cat. Correcta|✏️ Beneficiarios Correctos|✏️ Método Pago|
|-|-|-|-|-|-|-|-|-|-|
|59|**David** *(mesada mensual)<br /><br />R=No es mesada, es colegiatura*|14|$8,000–$19,000|Norma|`TRANSFERENCIAS\_FAMILIARES.DAVID`|dav|`ESCUELA.DAVID`|dav|efectivo|
|60|**Pau** *(mesada mensual)<br /><br />R=No es mesada, es colegiatura*|13|$800–$10,000|Ambos|`TRANSFERENCIAS\_FAMILIARES.PAU`|pau|`ESCUELA.PAU`|pau|efectivo|
|61|**Santiago** *(mesada mensual)<br /><br />R=No es mesada, es colegiatura*|6|$6,600–$7,000|Norma|`TRANSFERENCIAS\_FAMILIARES.SANTIAGO`|san|`ESCUELA.SANTIAGO`|san|efectivo|
|62|**Inscripcion Santi**|2|$2,000–$5,000|Norma|`TRANSFERENCIAS\_FAMILIARES.SANTIAGO`|san|`ESCUELA.SANTIAGO`|san|efectivo|
|63|**Santiago Abril** *(colegiatura mensual de Abril)*|2|$3,000–$4,300|Norma|`TRANSFERENCIAS\_FAMILIARES.SANTIAGO`|san|`ESCUELA.SANTIAGO`|san|efectivo|
|64|**Santiago Febrero**|2|$2,000–$5,000|Norma|`TRANSFERENCIAS\_FAMILIARES.SANTIAGO`|san|`ESCUELA.SANTIAGO`|san|efectivo|
|65|**Santiago Marzo**|2|$2,200–$5,100|Norma|`TRANSFERENCIAS\_FAMILIARES.SANTIAGO`|san|`ESCUELA.SANTIAGO`|san|efectivo|
|66|**David Abril** *(colegiatura mensual de Abril)*|1|$15,000|Norma|`TRANSFERENCIAS\_FAMILIARES.DAVID`|dav|`ESCUELA.DAVID`|dav|efectivo|
|67|**David Febrero**|1|$9,000|Norma|`TRANSFERENCIAS\_FAMILIARES.DAVID`|dav|`ESCUELA.DAVID`|dav|efectivo|
|68|**David Febrero y 3000 Marzo**|1|$8,000|Norma|`TRANSFERENCIAS\_FAMILIARES.DAVID`|dav|`ESCUELA.DAVID`|dav|efectivo|
|69|**David marzo**|1|$12,000|Norma|`TRANSFERENCIAS\_FAMILIARES.DAVID`|dav|`ESCUELA.DAVID`|dav|efectivo|
|70|**David Mayo**|1|$15,000|Norma|`TRANSFERENCIAS\_FAMILIARES.DAVID`|dav|`ESCUELA.DAVID`|dav|efectivo|
|71|**Santiago Inscripcion**|1|$8,800|Norma|`TRANSFERENCIAS\_FAMILIARES.SANTIAGO`|san|`ESCUELA.SANTIAGO`|dav|efectivo|
|72|**Santiago Mayo**|1|$7,000|Norma|`TRANSFERENCIAS\_FAMILIARES.SANTIAGO`|san|`ESCUELA.SANTIAGO`|san|efectivo|

> ⚠️ \*\*Nota\*\*: Los "David Abril", "Santiago Febrero" etc. parecen ser \*\*colegiaturas mensuales\*\* pagadas en la quincena en que caen, no mesadas regulares. ¿Debo crear subcategorías `TRANSFERENCIAS\_FAMILIARES.COLEGIATURA\_DAVID` y `TRANSFERENCIAS\_FAMILIARES.COLEGIATURA\_SANTI`?



R=Ya lo cambié a `ESCUELA.NOMBRE`

\---

## 📦 Otros (OTHERS)

|N°|Concepto|Quincenas|Monto|Quién paga|Cat. ETL actual|Benef. ETL actual|✏️ Cat. Correcta|✏️ Beneficiarios Correctos|✏️ Método Pago|
|-|-|-|-|-|-|-|-|-|-|
|73|**Psicologa** *(aparece también en PETS por error de hoja)*|19|$900|Norma|`OTHER` ❌→❓|nor, ben (default)|`OTHER.SALUD`|nor|efectivo|
|74|**Buro de credito 1 de 3**|1|$4,300|Norma|`OTHER` ❓|nor, ben (default)|`LOANS.BURO`|nor|efectivo|
|75|**Hawaiano** *(duplicado — también en ENTERTAINMENT)*|1|$350|Norma|`OTHER` ❌|nor, ben (default)|`OTHER.HAWAIANO`|pau|efectivo|
|76|**Inscripcion Santi** *(duplicado — también en TRANSFERENCIAS)*|1|$9,600|Norma|`OTHER` ❌|san|`ESCUELA.SANTIAGO`|san|efectivo|
|77|**Pau** *(duplicado — también en TRANSFERENCIAS)*|1|$800|Norma|`OTHER` ❌|pau|`TRANSFERENCIAS\_FAMILIARES.PAU`|pau|efectivo|
|78|**Prestamo 1 Omar**|1|$5,500|Norma|`OTHER` ❌→`LOANS.OMAR`|nor, ben (default)|`LOANS.OMAR`|nor|efectivo|
|79|**Prestamo 2 (Omar)**|1|$5,500|Norma|`OTHER` ❌→`LOANS.OMAR`|nor, ben (default)|`LOANS.OMAR`|nor|efectivo|

\---

## ❓ Preguntas abiertas para el usuario

Responde estas preguntas claves que afectan muchos registros:

### Q1 — Gasolinas

> Los conceptos \*\*Gasolina Camioneta\*\*, \*\*Gasolina Chochecito\*\*, \*\*Cochecito\*\* y \*\*Gasolina\*\* (genérica) están todos bajo `TRANSPORTATION.GASOLINA` con beneficiarios `Norma, Benjamín (default)`.
> - ¿\*\*Gasolina Camioneta\*\* → beneficiario es solo \*\*Norma\*\*?
> - ¿\*\*Gasolina Chochecito / Cochecito\*\* → beneficiarios son \*\*Agustín, David, Pau\*\* (÷3)?
> - ¿\*\*Gasolina\*\* (genérica) → ¿a cuál vehículo pertenece?

**Tu respuesta: Camioneta si es de Norma, Cochecito es de Agustín, David y Pau, genérica es Camioneta**

### Q2 — Servicios del hogar (Agua, Internet, Electricidad)

> Actualmente el ETL asigna `Norma, Benjamín (default)` como beneficiarios.
> - ¿Deberían ser \*\*todos los miembros del hogar\*\* (`todos`)?
> - ¿O solo los adultos (`adultos`)?

**Tu respuesta: Todos los miembros del hogar**

### Q3 — Hipoteca

> Actualmente: beneficiarios = `Norma, Benjamín`.
> - ¿Es correcto, o solo es de \*\*Benjamín y Norma\*\* (ya está bien)?

**Tu respuesta: Si, 'adultos' nada más.**

### Q4 — "Pau, David, Agus" en Seguros Médicos

> El ETL detecta correctamente → `SEGUROS\_MEDICOS.HIJOS`.
> - En la conversación anterior mencionaste que "Seguro hijos" es \*\*mesada\*\*, no seguro médico.
> - ¿Este renglón "Pau, David, Agus" ($3,000–$3,500) es el \*\*seguro médico real\*\* de los tres?
> - ¿O es también una mesada/transferencia que debe ir a `TRANSFERENCIAS\_FAMILIARES`?

**Tu respuesta: Exactamente, todos esos (menos el único registro de Benjamín) son mesadas y "Pau, David, Agus" se divide entre los 3, estas van como** `TRANSFERENCIAS\_FAMILIARES.NOMBRE`

### Q5 — Psicóloga

> Aparece 19 veces en `OTHERS` y 1 vez en `PETS` (por error de hoja). $900/quincena, paga Norma.
> - ¿A quién es la psicóloga? ¿De quién es el gasto? (¿Norma? ¿un hijo?)
> - ¿Qué categoría correcta? ¿`SERVICIOS\_EXTERNOS.PSICOLOGA`? ¿`PERSONAL\_CARE`?

**Tu respuesta: Es de norma, lo puse como salud, no es PETS, eso ha de haber sido un error.**

### Q6 — "Suscripcion nivel 6"

> $129–$130/quincena, 17 veces. ¿Qué servicio es este?

**Tu respuesta: Meli+.**

### Q7 — "Didi tarjeta" vs "Didi"

> - \*\*Didi\*\* (en LOANS, 18 quincenas, $778–$1,049): ¿es el pago de la Didi Card?
> - \*\*Didi tarjeta\*\* (en ENTERTAINMENT, 6 quincenas, $4,840–$6,500): ¿es el pago del saldo total de la Didi Card?
> Si ambos son pagos de tarjeta, deberían ir a `LOANS`, no `ENTERTAINMENT`.

**Tu respuesta: Ambos son tarjetas, es LOANS.**

### Q8 — "Mary"

> Aparece 3 veces en LOANS, $2,404, sin dato de quién paga. ¿Quién o qué es Mary?

**Tu respuesta: Una conocida de Norma.**

### Q9 — Comida, Despensa, Limpieza

> Las 3 tienen beneficiarios `Norma, Benjamín (default)`.
> - ¿Deberían ser \*\*todos\*\* (`todos`)?

**Tu respuesta: Si, deberían ser todos.**

### Q10 — Alimentación de las gatas

> `PETS.COMIDA`, beneficiarias actuales: `Norma, Benjamín`.
> - ¿Correcto o debería ser `todos` o solo `adultos`?

**Tu respuesta: Deberían ser todos.**

\---

## Resumen Estadístico

|Sección|Conceptos|Quincenas promedio|
|-|-|-|
|Vivienda|10|13.2|
|Transporte|4|16.5|
|Seguros Médicos|4|26.3|
|Alimentación|3|33|
|Mascotas|2|17|
|Entretenimiento|14|10.4|
|Préstamos/Tarjetas|21|6.9|
|Transferencias Familiares|14|3.8|
|Otros|7|3.4|
|**TOTAL**|**79**|—|



