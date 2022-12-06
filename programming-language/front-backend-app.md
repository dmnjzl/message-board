Software has two things: build a software and manage/running a software.

In software industry, build a software is called development. Manage/running a softwareUse is called operation, which includes compile and deploy application to cloud (AWS) or enterprise cluster, monitor application running status and restart the application if it dies, make application available at anytime (7x24 service), and manage the number of running application according to user's request to the application (scale management). Related to software operation are works of security, network, database, etc. Therefor, a project manager,could be a manager for development, or a manager for operation. Including software opeation into backend is incorrect -- a wrong statement I made before, and I want clear it now. Acturally, software operation is high pay position and a lot of new tools are used in software operation.

Lets' talk abount front and backend in an application.

An application must be used by some user, and it must use other applications. The way that application provides for user to use is called interface. If a user is a real person, then the interface is call User Interface (UI). If a user is another application (or program), it is called Applicaton Programing Interface (API). 

There are two way to distinguish front and backend.

1. by application: If an application has some UI, then it is a front application. If an application has no UI, then it is a backend application. The front application structure are very similar. The major difference is among the business the application implements. The backend application structure may very different. Here are some backend applications: Oracle (database), Jenkins (application deployment tool), WAS (cloud), OpenShift (Container management tool), Windows or Linux (OS). You can see the difference between front and backend.

2. by person's work: If a person works on UI, then he is a front developer. If a person works on all other parts of application, including API, using other applications, and implement business rules, he is a backend developer.

HTML and CSS are special languages for building UI. But UI also requires other language, like java-scripts or python, and other tools. Howver, backend code does not use and need HTML and CSS.

These are the difference between front and backend I can think about. 