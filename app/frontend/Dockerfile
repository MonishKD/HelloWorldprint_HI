#This file basically acts like the pc that builds frontend and evenutally runs it.

#YUP ROUND 2 BABY

#BUILD & RUN SECTION (stage)

#Fronend is a node project React+Vite so contained needs Node now.
FROM node:22

#folder in where everything will live in frontend files live.
WORKDIR /frontend

# Copies package.json and package-lock.json to the container. This is important
#because it allows us to install dependencies before copying the rest of the files,
# which can help with caching and speed up the build process.
COPY package*.json ./

#Self explanatory, this is the command to install the dependencies defined in package.json.
#It will create a node_modules folder in the container. no need for "npm build" since
#"npm run dev" does the build and running in ONE at the RUN SECTION stage
RUN npm install

#COPY the rest of the frontend files to the container. This includes all the source code,
#configuration files, and any other assets needed for the frontend application.
COPY . .

# Frontend has nothing to compile before it runs. Actually tbh, it does compile for Typescript but the Vite
# dev server handles it automatically from the source files. So one image can do all that, install, copy and start.

#So we go straight to giving ourselves a port note to our current image
# Vite's dev server listens on port 5173. This line is only a note, like EXPOSE 8080 in the backend's
# Dockerfile. The docker-compose.yml "ports:" line is what actually opens it.
EXPOSE 5173

#DONT BE SCARED
# Starts the Vite dev server "--host" is needed. Thats what it accepts connection from (whatever that means) jk
# vite listens to localhost by default, which in the container, means itself, but the browser cant reach it, so
# --host makes it listen on all addresses, so the port you open with "port:" in docker-compose.yml can reach it.
# Try it out without --host and you will see what I mean.

#npm run dev just starts the dev server. U are familiar of this :D
CMD ["npm","run", "dev","--","--host"]
