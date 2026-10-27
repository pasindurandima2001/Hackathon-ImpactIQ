# ImpactIQ Frontend

React + JSX frontend built with Vite, Tailwind CSS, React Router, and Axios.

## Setup

```sh
npm install
```

Set `VITE_API_BASE_URL` to the Spring Boot API base URL. For local development, copy `.env.example` to `.env.local`; the Vite dev server also proxies `/api` to `http://localhost:8080` when using the default relative base URL.

```sh
npm run dev
```

The frontend communicates only with the Spring Boot API. Gemini credentials remain server-side.
