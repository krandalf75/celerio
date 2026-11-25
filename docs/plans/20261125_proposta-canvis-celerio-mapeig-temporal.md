# Mapeig temporal a Celerio

## Objectiu

Adaptar les anotacions temporals generades al runtime objectiu, preservant la
compatibilitat amb les sortides existents i sense canviar els tipus Java ni les
signatures de les aplicacions generades.

Jakarta Persistence 3.2 depreca `@Temporal` i `TemporalType` per a codi nou.
Hibernate ORM 7 emet l'avís `HHH90000033` quan troba aquesta anotació. És un
avís de deprecació: Hibernate 7 no l'ha eliminat ni la rebutja necessàriament.
Spring Boot 4 utilitza habitualment Hibernate 7, però la versió efectiva del
provider pot variar; per tant, Celerio no ha d'inferir l'estratègia a partir de
la seva pròpia versió ni de la versió de Spring Boot.

El canvi ha de preservar el tipus JDBC `DATE`, `TIME` o `TIMESTAMP` de les
columnes afectades. Això és diferent de preservar la precisió fraccionària del
temps: només se'n pot garantir la conservació quan les metadades del model la
capturen i les proves la verifiquen.

### Abast

Actualment, `JpaAttribute` genera `@Temporal` per a `java.util.Date` quan el
tipus JDBC és `DATE`, `TIME` o `TIMESTAMP`. Aquest és l'abast d'aquest canvi.
El tractament de `Calendar` no s'amplia: el codi actual ja no el gestiona.
Els altres tipus Java no han de canviar de comportament en aquesta proposta.

## Comportament a implementar

| `temporalMapping` | Sortida | Ús |
|---|---|---|
| `legacy` | Manté `@Temporal(DATE/TIME/TIMESTAMP)` i els imports actuals. | Compatibilitat amb la sortida heretada i els runtimes existents. |
| `hibernate7` | No genera `@Temporal`; preserva el tipus JDBC. Afegeix el mapeig Hibernate explícit quan el mapeig per defecte no coincideix amb el tipus JDBC de la columna. | Projectes amb Hibernate ORM 7 o superior. |

No s'ofereix un mode `providerDefault`: delegar el tipus JDBC al provider sense
garantir que coincideixi amb les metadades de la columna faria ambigu el
contracte de generació.

Hibernate ORM 7 mapeja `java.util.Date` a `TIMESTAMP` per defecte. Per tant, en
mode `hibernate7`, les combinacions `java.util.Date` + JDBC `DATE` i
`java.util.Date` + JDBC `TIME` han de generar, respectivament,
`@JdbcTypeCode(SqlTypes.DATE)` i `@JdbcTypeCode(SqlTypes.TIME)`. Per a
`java.util.Date` + JDBC `TIMESTAMP`, no cal mapeig JDBC explícit si les proves
confirmen que el provider seleccionat manté aquest valor per defecte.

La compatibilitat antiga no ha de requerir dependències modernes d'Hibernate.
En canvi, el mode `hibernate7` necessita les anotacions d'Hibernate i el
namespace Jakarta. La sortida `legacy` amb Jakarta Persistence 3.2 encara pot
compilar, però `@Temporal` hi és deprecat.

## Configuració

La configuració serà un paràmetre del `celerio-maven-plugin`, igual que
`jakartaNamespace`. S'exposarà al plugin Maven i a la propietat Maven
corresponent; no serà una propietat de pack ni tindrà precedència definida per
pack. El valor predeterminat general serà `legacy`.

```xml
<configuration>
    <jakartaNamespace>true</jakartaNamespace>
    <temporalMapping>hibernate7</temporalMapping>
</configuration>
```

La propietat Maven serà `celerio-maven-plugin.temporalMapping`. Valors acceptats,
en minúscula i exactes:

- `legacy`
- `hibernate7`

Un valor desconegut ha de produir un error clar que enumeri els valors
acceptats. També s'ha de fallar abans de generar codi si se selecciona
`hibernate7` amb `jakartaNamespace=false`; no s'ha de canviar el namespace
automàticament.

El plugin ha de fer arribar el valor a la lògica de generació temporal abans de
cridar `producer.produce()` i netejar-lo en un bloc `finally`, seguint el cicle
de vida que `GenerateMojo` aplica a `jakartaNamespace`. La decisió no ha de
quedar limitada a reescriure imports: l'ha de poder consultar la lògica que
genera les anotacions de l'atribut.

## Canvis al model i a les anotacions

1. **Consultar metadades semàntiques.** Fer servir el `JdbcType` de la columna i
   el `MappedType` de l'atribut; no analitzar text d'anotacions renderitzades.
2. **Mantenir els predicats del model.** `isJavaUtilOnlyDate()`,
   `isJavaUtilDateAndTime()` i `isJavaUtilOnlyTime()` han de continuar
   retornant els mateixos resultats. La selecció del mode no els ha de modificar.
3. **Centralitzar la decisió amb les anotacions JPA.** La lògica actual és a
   `JpaAttribute.getTemporalAnnotation()`. Ha de decidir si emet l'anotació JPA
   o el mapeig Hibernate en funció del mode i les metadades.
4. **Preservar el tipus JDBC.** En mode `hibernate7`, generar un mapeig explícit
   per als casos `java.util.Date` + `DATE` i `java.util.Date` + `TIME`. Per a
   `java.util.Date` + `TIMESTAMP`, mantenir el mapeig per defecte de Hibernate 7.
   La resta de tipus Java manté la sortida actual i queda fora d'aquest canvi.
5. **Gestionar imports per fitxer.** Afegir `JdbcTypeCode` i `SqlTypes` només
   quan es genera `@JdbcTypeCode`; afegir `Temporal` i `TemporalType` només en
   mode `legacy`.
6. **No canviar contractes Java.** No transformar indiscriminadament els tipus
   Java a `LocalDate`, `LocalTime` o `Instant`.
7. **Eliminar postprocessaments.** La sortida ha de ser correcta directament
   des de Celerio, sense scripts que eliminin anotacions o imports amb
   substitucions globals de text.

## Proves d'acceptació

1. **Configuració del plugin:** comprovar que el valor predeterminat és
   `legacy`, que es pot configurar al plugin i mitjançant la propietat Maven, i
   que un valor desconegut falla amb els valors acceptats al missatge.
2. **Mode heretat:** generar els casos `java.util.Date` amb JDBC `DATE`, `TIME`
   i `TIMESTAMP`; comprovar que les anotacions i els imports coincideixen amb la
   sortida històrica. Abans de donar la prova per tancada, enumerar les versions
   JPA/provider antigues que s'han de suportar i compilar-hi la sortida. Aquesta
   matriu no consta al repositori actual.
3. **Mode Hibernate 7:** comprovar la matriu definida per al tipus Java i el
   `JdbcType`; no s'ha de generar `@Temporal`. Per a `java.util.Date`, verificar
   que `DATE` i `TIME` generen els codis Hibernate explícits i que `TIMESTAMP`
   conserva el mapeig per defecte. Els imports Hibernate només han d'aparèixer
   quan s'utilitzen.
4. **Tipus resolt i persistència:** amb Hibernate 7 i un dialecte suportat,
   verificar el tipus JDBC resolt pel provider i el tipus SQL del DDL o de
   l'esquema. Llegir i escriure valors representatius de `DATE`, `TIME` i
   `TIMESTAMP`; fixar la zona horària per fer les proves deterministes i afegir
   un cas en una segona zona horària per detectar regressions de conversió.
5. **Namespace Jakarta:** confirmar que `hibernate7` amb
   `jakartaNamespace=true` genera imports `jakarta.*`, i que combinar
   `hibernate7` amb `jakartaNamespace=false` falla abans de generar.
6. **Compatibilitat de namespaces:** en mode `legacy`, comprovar tant la sortida
   anterior `javax.*` com la sortida `jakarta.*` quan s'activi
   `jakartaNamespace`.
7. **Predicats del model:** comprovar que els tres predicats temporals mantenen
   els resultats per als casos Java/JDBC coberts.
8. **Contractes generats:** comparar entitats, getters/setters, DTO i signatures
   REST; només poden variar les anotacions i els imports previstos.
9. **Precisió fraccionària:** si les metadades disponibles exposen aquesta
   precisió, comprovar-la per separat del tipus JDBC. No atribuir-ne la
   conservació a `@JdbcTypeCode` sense una prova del provider i del dialecte.
10. **Serialització i exportacions:** verificar que el format JSON i les
    exportacions no canvien.

## Criteris d'implementació

- `temporalMapping` és un paràmetre del `celerio-maven-plugin`, amb el mateix
  patró de configuració i cicle de vida que `jakartaNamespace`; `legacy` és el
  valor predeterminat.
- Els packs no defineixen ni sobreescriuen aquest valor.
- `hibernate7` no genera `@Temporal` i exigeix `jakartaNamespace=true`.
- El mapeig Hibernate explícit s'aplica als casos `java.util.Date` + JDBC
  `DATE`/`TIME`; no s'aplica indiscriminadament a altres tipus Java o columnes
  temporals.
- `Calendar` queda fora de l'abast d'aquesta proposta.

## Referències

- [Jakarta Persistence 3.2: `@Temporal`](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/temporal)
- [Hibernate ORM 7.0 User Guide: temporal data](https://docs.hibernate.org/orm/7.0/userguide/html_single/#mapping-types-temporal)
