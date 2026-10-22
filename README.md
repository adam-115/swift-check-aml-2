# Spring Boot 4 + Angular + Tailwind — un seul fat jar

## Structure

```
pom.xml                 parent / agrégateur (Java 21, Spring Boot 4.1.2)
frontend/               projet Angular (Tailwind v4) + pom.xml → app-frontend.jar (static/**)
back/                   Spring Boot + pom.xml → back/target/app.jar (fat jar, inclut le front)
```

## Créer le projet Angular (une fois)

Depuis la racine, générer Angular dans `frontend/` (à côté du `pom.xml` existant) :

```bash
npx @angular/cli@22 new frontend --directory frontend --routing --style=css --ssr=false --skip-git
```

Puis dans `frontend/` :

```bash
npm install tailwindcss @tailwindcss/postcss postcss
```

`frontend/.postcssrc.json` :

```json
{ "plugins": { "@tailwindcss/postcss": {} } }
```

`frontend/src/styles.css` :

```css
@import "tailwindcss";
```

Le projet Angular doit s'appeler `frontend` (sortie `dist/frontend/browser`), sinon adapter `frontend.dist` dans `frontend/pom.xml`.

Appels API côté Angular : préfixe `/api/...`. En dev, `frontend/proxy.conf.json` :

```json
{ "/api": { "target": "http://localhost:8080", "secure": false } }
```

puis `ng serve --proxy-config proxy.conf.json`.

## Build

```bash
mvn clean package
java -jar back/target/app.jar
```

Sans relancer le build Angular : `mvn package -Dskip.frontend=true`.

Pipeline Maven :
1. `frontend` — frontend-maven-plugin installe Node/npm dans `frontend/target/`, lance `npm install` puis `npm run build`, et emballe `dist/frontend/browser` sous `static/` dans `app-frontend.jar`
2. `back` — dépend de `app-frontend` ; spring-boot-maven-plugin produit `back/target/app.jar`

`SpaConfig` sert `classpath:/static` et renvoie `index.html` pour les routes Angular inconnues (sauf `/api/**` et `/actuator/**`).
