#This file basically acts like the pc that builds backend and evenutally runs it.

# BUILD SECTION (stage)

#Check in pom.xml or any backend file what jdk verison is being used and at least find a docker image
#that has that version of jdk. In this case, I used eclipse-temurin:25-jdk because the backend uses jdk 25.
FROM eclipse-temurin:25-jdk AS build

#instantiates a new directory and calls it our work directory inside the container. This is where all the files
#will be copied to and where the backend will be built. But FYI the build starts here but after compiling it goes
# to the target directory. Try compiling on ur pc and you will see what i mean, usually gitignore removes the target
# to not overload the repo with compiled files. But the target directory is where the compiled backend will be.
# this is important for next step when we want to run only the compiled backend and when we dont need the build anymore
WORKDIR /backend

#copies whatever is in the current directory from local device/repo to the container work directory
#(. just means here or current directory. takes 2 parameters, first= from where locally, second to where in the container)
COPY . .

#runs the build for maven's wrapper and packages needed for the backend.
# -DskipTests just skips the tests. Try it out locally and run without -DkskipTests to see what happens.
#It will run the tests and if any of them fail, the build will fail.
RUN ./mvnw package -DskipTests


##########################################################################################################################################


# RUN SECTION (stage)

#This is the final image that will be used to run the backend. It is a smaller image than the build image.
#Actually, this image is just for the runtime environment. It does not have the build tools like maven or jdk.
#It is just a jre image. JDK is like the kitchen, JRE is the table, you dont need the kitchen to eat on the table.
#Really, u just need this smaller image to run everything -> the backend really.
FROM eclipse-temurin:25-jre AS run

#This is the work directory for the run stage. Now note that you are using another "Mini computer" (container
#with the runtime environment image) so its not the same directory as the build stage, but we try to simulate
#same environment as build for consistency.
WORKDIR /backend

#This is the important part. You want to copy the build compiled version of the build image to the run image.
#so it brings the .jar file from the build image to the run image. The jar file is the compiled version of the backend.

# to read syntax, COPY from the build image above, the following file /backend/target/*.jar (the compiled backend)
# and copy it to the current directory of the run image
COPY --from=build /backend/target/*.jar .

#So u might be thinking, wdym port? backend has a port now? well yeah, just like the frontend.
#The backend exposes its port 8080 for Springboot as default. The way you can see this for now is
# Backend and Database live on different "mini computers" (containers) that have different access points
#via different ports. The backend exposes its port 8080 and the database exposes its port 5432.

#The frontend exposes its port 5173, and the browser loads the website from there. The website's code then runs in the
#browser, which calls the backend on port 8080. When the backend wants to talk to the database, it uses the database's port 5432.

# fyi (YOU CAN SKIP), u might be like wait what do u mean 3 containers. isnt that a lot? like how do they work together without the same
# ip? (assuming u thinking of localhost:8080 vs. localhost:1234)
# well with local host, it know its SAME IP (localhost or 127.0.0.1 ,same thing) and its DIFFERENT PORTS, but with docker,
#it creates different ip's under a shared network. This is in the background, you dont need to worry about setting up the
#network sharing since compose does it for you.
#Imagine zooming out just one level back port->ip->network (private/public)

#This code below only just gives a note to this image (JRE one) so that it knows that the backend exposes its port 8080.
# The docker-compose.yml file will then open the port in "ports:"
EXPOSE 8080


#This is the command that the container starts. Its not the same as run, since its for building. The container here doesn't build
# anything. Here the container executes. It starts here and it starts in our case, the backend.
# Equivalent of typing
# java -jar *.jar in terminal. The *.jar is the compiled backend that was copied from the build image to the run image.
# btw the * just means any file that ends with .jar. In our case, there is only one file that ends with .jar so it will run that.

# java : means run the java program. its executable since our image has the Java Runtime Environment (JRE) remember? the JRE is the
#        runtime environment for java programs. It has the java executable.
# -jar : means run the jar file. The jar file is the compiled backend that was copied from the build image to the run image.
#        The jar file is a java program that can be executed.
#
#        NOTE JAVA CAN RUN classes, a source file and etc... you prob have done this before in terminal "java HelloWorld.java" or "java HelloWorld.class"
#        but here we are running a jar file. A jar file is a java program that can be executed. A full program with its packages and dependencies.
#        It is like a zip file that contains all the classes and resources needed to run the program.
#
# .jar   last parameter is where u would put the exact name of the file. Since java is picky, we need to specify the  .jar  file and run it.
#        its the same that we did in the terminal when we ran the backend. We ran "java -jar *.jar" in the terminal. Its just *.jar is because we are lazy
#        and we know its 1 file
ENTRYPOINT ["java", "-jar", "backend-0.0.1-SNAPSHOT.jar"]
