# À propos de l'application Connecteur Moodle

* Licence : [AGPL v3](http://www.gnu.org/licenses/agpl.txt) - Copyright CGI
* Financeur(s) : CGI
* Développeur(s) : CGI
* Description : Module permettant la connexion à Moodle.

# Présentation du module 
Moodle permet de diffuser des documents (textes, audio, vidéo, etc.) et d’interagir avec les étudiants, à l'aide d'une variété d'outils de communication et de gestion.

## Configuration
<pre>
 {
  "config": {
    ...
    "timeSecondSynchCron": "${timeSecondSynchCron}",
    "timeCheckNotifs": "${timeCheckNotifs}",
    "numberOfMaxDuplicationTentatives": ${numberOfMaxDuplicationTentatives},
    "numberOfMaxPendingDuplication": ${numberOfMaxPendingDuplication},
    "timeDuplicationBeforeDelete": "${timeDuplicationBeforeDelete}",
    "address_moodle": "${moodleServer}",
    "ws-path": "${moodleWsPath}",
    "wsToken": "${wstoken}",
    "idStudent": ${idStudent},
    "idEditingTeacher": ${idEditingTeacher},
    "idAuditeur": ${idAuditeur},
    "idGuest": ${idGuest},
    "header": "${header}",
    "deleteCategoryId": ${deleteCategoryId},
    "publicBankCategoryId": ${publicBankCategoryId},
    "mainCategoryId": ${mainCategoryId},
    "userMail" : "${userMail}",
    "share": {
        "overrideDefaultActions": "${moodleDefaultShare}"
    }
  }
}
</pre>

Dans votre springboard, vous devez inclure des variables d'environnement :
<pre>
timeSecondSynchCron = ${String}
timeCheckNotifs = ${String}
numberOfMaxDuplicationTentatives = Integer
numberOfMaxPendingDuplication = Integer
timeDuplicationBeforeDelete = ${String}
moodleServer = ${String}
moodleWsPath = ${String}
wstoken = ${String}
idStudent = Integer
idEditingTeacher = Integer
idAuditeur = Integer
idGuest = Integer
header = ${String}
deleteCategoryId = Integer
publicBankCategoryId = Integer
mainCategoryId = Integer
userMail = ${String}
moodleDefaultShare = Array(String)
</pre>