@echo off
cd /d "D:\BioSecure Beta03\server"
set "PORT=3001"
set "MONGODB_URI=mongodb+srv://sapm3006_db_user:sergioponce1234@cluster0.kssppg3.mongodb.net/biosecure?retryWrites=true&w=majority"
set "GOOGLE_CLIENT_ID=103319710145-f2dd8416d5j060uue01mquo09n5n1pt8.apps.googleusercontent.com"
node server.js
pause
