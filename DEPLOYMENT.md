# SmartLMS Backend Deployment Guide (Render)

This backend is a Java 21 Spring Boot application configured for seamless deployment on [Render](https://render.com).

---

## 1. Quick Deployment on Render

### Option A: Using the Dockerfile (Recommended)
1. Push this repository to your GitHub account (e.g. `smartlms-backend`).
2. Log into [Render Dashboard](https://dashboard.render.com).
3. Click **New +** -> **Web Service**.
4. Connect your **smartlms-backend** GitHub repository.
5. In the settings:
   - **Name**: `smartlms-backend`
   - **Language / Environment**: `Docker`
   - **Branch**: `main`
   - **Region**: Choose closest to you (e.g., Oregon, Frankfurt, Singapore)
   - **Instance Type**: Free

---

## 2. Database Setup (Free MySQL)

Spring Boot requires a MySQL database. You can obtain a free cloud MySQL instance from:
- [TiDB Cloud (Serverless MySQL Compatible)](https://tidbcloud.com) - Free forever 5GB
- [Aiven for MySQL](https://aiven.io) - Free tier
- [Clever Cloud](https://www.clever-cloud.com) - Free MySQL add-on

### Setting Environment Variables in Render:
In the Render Web Service settings -> **Environment Variables**, add:

| Variable Name | Example / Description |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://<host>:<port>/<dbname>?useSSL=true&allowPublicKeyRetrieval=true` |
| `SPRING_DATASOURCE_USERNAME` | `<db_username>` |
| `SPRING_DATASOURCE_PASSWORD` | `<db_password>` |
| `JWT_SECRET` | Any strong 64-character hex or string (e.g. `404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970`) |

*(Note: `PORT` is automatically injected by Render and read by Spring Boot).*

---

## 3. Verify Health Check
Once deployed, verify the service is running:
```bash
curl https://<your-render-app-name>.onrender.com/api/health
```
Should return: `SmartLMS Backend is running!`
