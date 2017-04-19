[![Build Status](https://drone-butc.dci.sfdc.net/api/badges/modularization-team/modularity-enforcer/status.svg)](https://drone-butc.dci.sfdc.net/modularization-team/modularity-enforcer)

# Project modularity enforcer

* See [the design doc](./DESIGN.md)  for the intent and implied logic in this plugin.

* This project makes the assumptions of maven and partial build safe.

* This project also allows you to create a list of exceptions that make those assumptions unsafe.  Those exceptions are contained in a
```<reactor project root>/.mvn/basedir-enforcer.config```  file.

* The syntax is as follows:
```
# enforce
#
# How to read this file:
#
# Column1: The maven project's artifactId -- hope we never add dups in core.
# --- this makes an assumption that your reactor build has a single groupId
#     may be a `*` to apply to all projects
#
# Column2: R,W,E,P
#       Read Write... you should only write within your project even here
#       Execute in this case the third column will be the name of an executable on your path.
#       Project enforce nothing on this project.
#
# Column3: Leading slash indicates this is the reactor base
#		   no leading slash, relative to the pom
#
#      ${dependency.$ALL}/...  must be located in a dependency of the project.
#
#      path/${project.artifactId} put the current project artifacted in this replacement
#             only helpful if project column is *
#
#      ${depenendency.artifactId} put the dependency's artifactId on this replacement  
#             only meaningful if ${dependency.$ALL} was used. Like:
#             ${dependency.$ALL}/${depenendency.artifactId}.iml
#
#      ${dependency.<<ARTIFACTID>>}/... will let the project access only this dependency's resources.  
#             You should use this mechansim rather than using the /...to artifactId/... to access files, 
#             as the former is not a breaking rule, and the latter is.  
#             See the design doc for Kinda Okay and Not Okay reads.
#
```

* there are 5 system properties that may be set in your pom, settings file, or as a -D paramenter, they are:
  * ```modularity.enforcer.disabled``` turns off the modularity enforcer
  * ```modularity.enforcer.logonly``` the modularity enforcer will only log violations
  * ```modularity.enforcer.allow.breaking.execptions``` the modularity enforcer will let you create exceptions that are known to break modularity.   See the design doc for Kinda Okay and Not Okay reads.
  * ```modularity.enforcer.allow.read.default``` only usable if ```modularity.enforcer.allow.breaking.execptions``` is set.  Read is allowed for all non maven locations.
  * ```modularity.enforcer.allow.write.default``` only usable if ```modularity.enforcer.allow.breaking.execptions``` is set.  Write is allowed for all non maven locations.