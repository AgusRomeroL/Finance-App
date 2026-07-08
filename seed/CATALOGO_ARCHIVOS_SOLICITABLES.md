# Catálogo de archivos solicitables — datos de Norma para alimentar la app

> Guía para reunir los exports de datos que la app puede **ingerir y clasificar** (categoría + beneficiario + método de pago) más allá de los estados de cuenta bancarios. Documento PII-safe (sin números de cuenta). Generado 2026-07-08 como parte de la auditoría de método de pago + beneficiarios.

## Por qué estos archivos

Los **estados de cuenta** solo dicen el comercio (p. ej. "Amazon $458") — no *qué* se compró ni *para quién*. Los **exports de datos** de Amazon, Mercado Libre, PayPal y el SAT traen **nombre de producto, importe, fecha y a veces el destinatario** → permiten atribuir con precisión quién consumió qué y resolver las dudas de beneficiario que hoy quedan como "hogar equitativo" por falta de dato.

**Privacidad:** la app extrae el texto/las filas **localmente en el teléfono**; a la nube (NVIDIA) solo se manda el mínimo para clasificar (producto + monto + fecha), **nunca** direcciones ni datos personales. Los archivos crudos no salen del dispositivo.

---

## Prioridad ALTA — nivel producto (los que más resuelven la atribución)

### 1. Amazon — Historial de pedidos
- **Qué pedir:** "Solicitar mis datos" (Request My Data) → categoría **Your Orders** (o "Todo").
- **Dónde:** Amazon → Cuenta → buscar *"Privacidad"* / *"Solicitar tus datos"* (Privacy Central / DSAR).
- **Formato:** **ZIP** con varios CSV. El clave es `Retail.OrderHistory.1.csv` — columnas: *Order Date, Product Name, ASIN, Quantity, Unit Price, Total Owed, Shipping Address, **Gift Recipient**, Payment Instrument Type, Order Status*. También `Returns` (devoluciones) y pedidos digitales.
- **Tarda:** varios días (Amazon avisa por correo cuando está listo).
- **Resuelve:** qué compró cada quién (p. ej. las cremas de David), devoluciones, y el **medio de pago** por compra.

### 2. Mercado Libre — Tus compras
- **Qué pedir:** historial de compras.
- **Dónde:** Mercado Libre → Mi cuenta → **Compras** (o el centro de privacidad "Tus compras: historial de compras").
- **Formato:** CSV/Excel (fecha, producto/título, precio, vendedor, estado).
- **Resuelve:** producto por compra → beneficiario + categoría.

### 3. Mercado Pago — Tus movimientos de dinero
- **Qué pedir:** "Tus movimientos de dinero" (historial de retiros, pagos, dinero en cuenta y **créditos activos**).
- **Dónde:** Mercado Pago → Actividad → exportar reporte → elegir periodo + **CSV**.
- **Formato:** CSV (retiros, pagos, saldo, Mercado Crédito).
- **Resuelve:** movimientos de la wallet MP + el **préstamo/línea de crédito** (`LOANS.MERCADO_PAGO`); confirma el drawdown de $4,000 que pasó a Benjamín.

### 4. PayPal — Actividad
- **Qué pedir:** reporte de actividad (Activity Download).
- **Dónde:** PayPal → Actividad → Descargar → **Personalizado** → **CSV** (hasta 12 meses por corte; 7 años disponibles).
- **Formato:** CSV (ZIP si >50k registros); fecha, comercio, monto.
- **Resuelve:** confirma el **comercio real y su titular** de GitHub / Microsoft / Coursera / Adobe (todos pasan por PayPal) — crítico porque la clasificación previa los atribuía mal.

### 5. SAT — CFDI (facturas) descarga masiva
- **Qué pedir:** XML de facturas **emitidas** y **recibidas**.
- **Dónde:** portal SAT → Factura electrónica → *"Consulta y recuperación de comprobantes"* / descarga masiva. **Requiere e.firma (FIEL) vigente.**
- **Formato:** **XML** (hasta 5 años + año en curso; máx. 2,000 XML/día).
- **Resuelve:** fuente **autoritativa itemizada** (conceptos, importes, IVA). Las **emitidas** revelan **ingresos** (p. ej. PayClip "SERV AGUSTIN R", nómina Machine Apps) — clave para el dilema ingreso-vs-gasto.

---

## Prioridad MEDIA

### 6. Google — Takeout (Play + suscripciones)
- **Dónde:** takeout.google.com → seleccionar **"Google Play Store"** (y "Google Pay" si aplica).
- **Formato:** **JSON** (`Google Play Store/Purchase History.json`) + suscripciones (Play, YouTube Premium, Google One).
- **Resuelve:** compras y suscripciones de Play; confirma dueño de YouTube Premium / Google One.

### 7. Telcel / Movistar / Telefónica — factura itemizada
- **Qué pedir:** el detalle **por línea** del plan (PDF de factura o el detalle del portal).
- **Resuelve:** el **costo por persona** del plan telefónico (Norma/Normita/Santiago/David comparten ~$850.95/mes; identificar la línea Movistar ~$825.95).

### 8. Bancos — export CSV de movimientos
- **BBVA, Banamex (MiCuenta/Clásica), Klar, BanCoppel, DiDi Card:** si ofrecen "descargar movimientos" en CSV/Excel, son **más limpios que los PDF** (evitan errores de OCR).
- **Resuelve:** método de pago real por transacción sin OCR.

### 9. Apps de consumo (opcional)
- **DiDi / Uber:** historial de viajes y pedidos (DiDi Food).
- **Rappi:** historial de pedidos.
- **Netflix / Spotify / HBO Max:** historial de facturación (confirmar dueño del plan).

---

## Cómo entregarlos
- Guardar los archivos **tal cual** (ZIP / CSV / XML / JSON / PDF). La app los extrae localmente.
- **Orden de valor para la auditoría de beneficiarios:** Amazon → Mercado Libre → PayPal (resuelven "quién consumió"). Para ingresos/precisión fiscal: SAT CFDI. Para el plan telefónico: factura Telefónica/Movistar.

## Tabla resumen

| # | Fuente | Qué exportar | Formato | Prioridad | Resuelve |
|---|--------|--------------|---------|-----------|----------|
| 1 | Amazon | Historial de pedidos (Request My Data) | ZIP/CSV | ALTA | Producto + destinatario + medio de pago |
| 2 | Mercado Libre | Tus compras | CSV/Excel | ALTA | Producto → beneficiario |
| 3 | Mercado Pago | Movimientos de dinero + créditos | CSV | ALTA | Wallet MP + préstamo |
| 4 | PayPal | Actividad | CSV/ZIP | ALTA | Titular real de GitHub/Microsoft/Coursera/Adobe |
| 5 | SAT | CFDI emitidas + recibidas | XML | ALTA | Itemizado autoritativo + ingresos |
| 6 | Google | Takeout (Play) | JSON | MEDIA | Suscripciones/compras Play |
| 7 | Telefónica/Movistar | Factura itemizada | PDF | MEDIA | Costo telefónico por persona |
| 8 | Bancos | Movimientos CSV | CSV | MEDIA | Método de pago sin OCR |
| 9 | DiDi/Uber/Rappi/streaming | Historial | CSV/PDF | BAJA | Detalle de consumo/plan |

---

## Soporte en la app (feature de ingesta generalizada)

La pantalla de import se generaliza para aceptar, además de estados de cuenta (PDF/imagen), estos formatos estructurados:
- **CSV / ZIP** (Amazon, Mercado Libre/Pago, PayPal, bancos) — parseados localmente, sin OCR.
- **XML** (SAT CFDI) — parseo local de conceptos/importes.
- **JSON** (Google Takeout).

Cada archivo se etiqueta con su **tipo de documento** (estado de cuenta / compras / movimientos de dinero / factura CFDI) y se clasifica con NVIDIA por producto → categoría + beneficiario, con **anti-doble-conteo** contra lo ya sembrado (para no contar dos veces una compra que también aparece en el estado de la tarjeta). Detalle técnico en el plan de la sesión (workstream E).
