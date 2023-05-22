

# Discriminating Sub-Pattern features Learning (DiSPaLe) from user preferences

Ce repertoire reprend le framework [letsip](https://bitbucket.org/wxd/letsip/src/master/) développé par Vladimir Dzyuba et al.
letsip est complété par trois nouveaux outils : 
- un nouvel oracle qui permet d'extraire des motifs diversifiés
- un nouveau descripteur qui exploite la notion de motifs discriminant
- une méthode d'aagrégation des poids appris par le module *scd*.


## 1- Compilation
Avant d'effectuer une exécution, il faudra au préalable compiler le projet **dispale**.
Pour cela, on pourra utiliser le script **compile.sh** situé à la racine du projet.
Il est également possinle d'effectuer une compilation manuelle. Pour cela, il faut se placer à l'intérieur du dossier **code** et faire :

```
> gradle wrapper --gradle-version=4.9
> ./gradlew build
```

## 2- Execution
Le programme est implémenté en java et en scala. Les fichiers exécutables générés sont des _jar_.
L'exécution se fait en utilisant le script python **launch_LetSIP** qui se trouve dans le répertoire _scripts/tests_. Ce scripts donne la possibilité de donner les différentes configurations et données d'entrées pour le programme.
Il est également possible d'exécuter le programme de façon manuelle en utilisant une commande java et en spécifiant les arguments nécessaires. Pour cela, il suffit d'exécuter la commande suivante :

**java -classpath CLASSPATH_JARS PROGRAMME ARGS**

avec :
- CLASSPATH\_JARS: il s'agit d'un ensemble de fichier jars; il suffit de le remplacer par le contenu du fichier **classpath_jars.txt**
- PROGRAMME = be.kuleuven.lewvits.Main
- il s'agit d'un ensemble de paramètres à envoyer

### Exemples
Pour avoir un affichage expliquant les différents arguments :
```
java -classpath CLASSPATH_JARS ./chemin/vers/les/fichiers/jars be.kuleuven.lewvits.Main -h
```

Autre exemple
```
java -classpath CLASSPATH_JARS ./chemin/vers/les/fichiers/jars be.kuleuven.lewvits.Main -m 1 -d ./datasets/ijcai16.txt -FI ./data/ijcai16.fimi -f 2 -k 5 -i 10 -r SurprisingnessRanker -F Items -l 1 -t 10.0 -FU all -s 1012177420835401612
```

Autre exemple
```
java -classpath CLASSPATH_JARS ./chemin/vers/les/fichiers/jars be.kuleuven.lewvits.Main -m 2 -d ./datasets/vote.txt -FI ./data/vote.fimi -f 10 -k 10 -i 20 -r SurprisingnessRanker -F Items-Transactions -l 0 -t 10.0 -FU all -s 4512173020835871034 -e 0.15
```


