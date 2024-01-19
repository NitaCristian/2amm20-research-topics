

# Discriminating Sub-Pattern features Learning (DiSPaLe) from user preferences

This repository contains the implementation of DiSPaLe, our interactive mining approach 
that was published at [PAKDD-2023](https://link.springer.com/chapter/10.1007/978-3-031-33374-3_20). 
In this paper, we propose a new interactive pattern mining approach that introduces more 
complex class of descriptors for *explainable ranking*, thereby allowing to capture the 
importance of item interactions. These descriptors exploit the concept of **discriminating sub-patterns**, 
which separate patterns that are given low rank by the user from those with high rank. 
By temporarily adding those descriptors, we can learn weights for them, which are 
then apportioned to involved items without blowing up the feature space. 
Results on [UCI](https://archive.ics.uci.edu/datasets) and [CP4IM](https://dtai.cs.kuleuven.be/CP4IM/datasets/) datasets show favourable trade-offs in quality-time of learning.

## 1- Discriminating features
Discriminating patterns are correlated with the user's ranking and can therefore be used to explain it.
The figure below provides a schematic overview of how DiSPaLe operates.
![The dispale framework](paper/dispale-new.eps "The dispale framework")

The paper's supplementary materials can be found in the ['paper'](https://gitlab.com/phdhien/dispale/-/tree/main/paper?ref_type=heads) directory.
A tutorial and an illustrative example of discriminating features are also included.



## 2- Building the project
Before executing, you must first compile the project.

To do this, navigate to the project's root directory and run the **build-code.py** Python script.

**Build the project using the *build* parameter**
```
> python build-code.py build
```

**Clean the previously built project using the *clean* parameter**
```
> python build-code.py clean
```

If everything went well, you will receive an exit status of 0.


## 2- Execution
The program has been implemented using Java, Scala, and Python. The executable files generated are JAR files.
Executions can be carried out using the **[test-program.py](https://gitlab.com/phdhien/dispale/-/blob/main/scripts/test-program.py?ref_type=heads)** Python script.

You can find bellow some examples commands:

For help with the parameters:
```
> python scripts/test-program.py -h
```

Launch DiSPaLe on *chess* dataset with a frequency threshold of 0.5 (50% of the transactions in the dataset),
queries size equal to 5, *Flexics* sampler as the oracle, the *Eflexics* algorithm, *Items* as features, 
0 as the quey retention value and 10 iterations loops:
```
> python3 scripts/test-program.py -m 'dispale' -d 'chess' -f 0.5 -k 5 -o 'flexics' -a 'eflexics' -F 'Items' -l 0 -i 10 -to 100
```

Launch [letsip](https://bitbucket.org/wxd/letsip/src/master/) on *german-credit* dataset with a frequency threshold of 0.25,
queries size equal to 10, *Flexics* sampler as the oracle, the *Eflexics* algorithm, *Items-Transactions-Length* as features, 
1 as the quey retention value and 25 iterations loops:
```
> python3 scripts/test-program.py -m 'letsip' -d 'german-credit' -f 0.25 -k 10 -o 'flexics' -a 'eflexics' -F 'Items-Transactions-Length' -l 1 -i 25 -to 600
```

We recently propose at [EGC-2024](https://iutdijon.u-bourgogne.fr/egc2024/programme/) a method for interactive mining of High Utility Itemsets (HUI). This method is denoted **LUTOM**.

Launch lutom on *mushroom* dataset setting queries size equal to 3, 
*huiminer* as the oracle, the [*tkuce*](https://link.springer.com/chapter/10.1007/978-3-030-55789-8_72) algorithm, 
*Items-Transactions* as features, 0 as the quey retention value and 12 iterations loops:
```
> python3 scripts/test-program.py -m 'lutom' -d 'mushroom' -k 3 -o 'huiminer' -a 'tkuce' -F 'Items-Transactions' -l 0 -i 12
```

Launch lutom++Discriminating patterns on *hepatitis* dataset setting queries size equal to 7, 
*huiminer* as the oracle *huiminer* as the oracle, the [*haisampler*](https://link.springer.com/chapter/10.1007/978-3-031-05936-0_11) algorithm, *Items* as features, 
2 as the quey retention value and 12 iterations loops:
```
> python3 scripts/test-program.py -m 'lutomDisc' -d 'hepatitis' -k 7 -o 'huiminer' -a 'haisampler' -F 'Items' -l 2 -i 12
```





