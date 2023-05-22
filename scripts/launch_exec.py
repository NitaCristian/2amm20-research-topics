'''
Created on 23 Feb 2023

@author: Lobnury
'''

import os
import sys
import time
import math
import subprocess
import multiprocessing as mp
from multiprocessing import Process
#
nbP = 1 # number of project to build in parallel
timeout = "91800s" # or "86400s"
#
#---------------------------------------------------------------------------------
#---------------------------------------------------------------------------------
#
#all_dataname = ["mushroom"]
all_dataname = ["hepatitis"]
#all_dataname = ["BMS1", "chess", "connect", "heart-cleveland", "hepatitis", 
#                    "kr-vs-kp", "mushroom", "retail", "splice1", "T10I4D100K", "T40I10D100K"]
#
#++++++++++++++++++++++++++++++++++++++++++++++++++++++++
#
thresholds = {
        "lymph.txt" : [0.32],
        "hepatitis.txt" : [0.35],
        "zoo-1.txt" : [0.09],
        "german-credit.txt" : [0.35],
        "vote.txt" : [0.3],
        "soybean.txt" : [0.044],
        "mushroom.txt": [0.3],
        "anneal.txt" : [0.81],
        "ijcai16.txt" : [0.1],
}
#
#++++++++++++++++++++++++++++++++++++++++++++++++++++++++
#
tilt = 10.0
nb_iterations = 10
nb_repetitions = 1
seed = "42085406528110384"
#
#++++++++++++++++++++++++++++++++++++++++++++++++++++++++
#
all_methods_to_launch = ["letsip"]
#all_methods_to_launch = ["dispale", "letsip"]
#
dispale_aggregation = ["EXP", "LIN"] # Exponential and Linear
#
all_eta = [0.13]
#all_eta = [0.33, 0.25, 0.20, 0.17, 0.13]
#
#++++++++++++++++++++++++++++++++++++++++++++++++++++++++
#
all_querySizes = [5]
#all_querySizes = [3, 5, 10]
#
#++++++++++++++++++++++++++++++++++++++++++++++++++++++++
#
all_query_retentions = [1]
#all_query_retentions = [0, 1]
#
#++++++++++++++++++++++++++++++++++++++++++++++++++++++++
#
all_oracles = ["EFlexics"]
#
#++++++++++++++++++++++++++++++++++++++++++++++++++++++++
#
all_rank_functions = ["SuprisingnessRanker"]
#all_rank_functions = ["SuprisingnessRanker", "FrequencyRanker"]
#
all_features = ["Items"]
#all_features = ["Items", "Items-Transactions", "Items-Transactions-Frequency-Length"]
#
#++++++++++++++++++++++++++++++++++++++++++++++++++++++++
#
# jaccard diversity thresholds
all_jmax = [0.05]
#all_jmax = [0.05, 0.1, 0.2]
#
#*******************************************************************************************************************
#*******************************************************************************************************************
#
def create_folder(folder):
    os.makedirs(folder,exist_ok=True)
#
#++++++++++++++++++++++++++++++
#
def nbrTrans(dataset_file):
    with open(dataset_file, "r") as infile:
        nbr_trans = 0
        for line in infile:
            if line.rstrip() and line[0] != '@':
                nbr_trans += 1
        return nbr_trans
#
#++++++++++++++++++++++++++++++
#
def get_method_config(method):
    config = ()
    if method == "dispale":
        for aggreg in dispale_aggregation:
            for eta in all_eta:
                config = (aggreg, eta, )
    return config
#
#=====================================================================================
#=====================================================================================
#
def run_dispale(queue, config, root_dir, results_dir):
    try:
        (i, timeLimit, dataname, data_file, freq, minsup, method, features, querySize, queryRetention, ranker) = config[:11]
        #
        if not os.path.exists(data_file):
            print(f"\nIMPOSSIBLE TO LAUNCH EXECUTION OF {project}.\nTHE GIVEN DATASET IS MISSING...\n")
            sys.exit(1)
        #
        #-------------------------------------------------------------------
        #
        oracle = all_oracles[0]
        #seed_ = str(hash(time.time()))
        seed_ = str(abs(hash(dataname)) + i)
        #
        (aggreg, eta) = ("", 0)
        if method == "dispale":
            (aggreg, eta) = config[11:13]
        #
        #-------------------------------------------------------------------
        #
        # output dir and files
        output_dir = os.path.realpath(os.path.join(results_dir, "XPs", dataname, str(freq), features))
        output_dir = os.path.realpath(os.path.join(output_dir, "k_"+str(querySize), "l_"+str(queryRetention), "eta_"+str(eta)))
        if aggreg:
            output_dir = os.path.realpath(os.path.join(output_dir, aggreg))
        output_dir = os.path.realpath(os.path.join(output_dir, method))
        create_folder(output_dir)
        #
        filename = dataname + "-f_" + str(freq)
        logs_file = os.path.realpath(os.path.join(output_dir, filename + ".log"))
        out_file = os.path.realpath(os.path.join(output_dir, filename + ".out"))
        time_file = os.path.realpath(os.path.join(output_dir, filename + ".time"))
        #
        #-------------------------------------------------------------------
        #
        # get dependencies
        dependencies = "code/dispale/build/libs/dispale-0.0.1-SNAPSHOT.jar:code/scd/build/libs/scd-0.0.1-SNAPSHOT.jar"
        #
        dependencies += ":code/lib/pf4cs-1.0.5.jar:code/lib/slf4j-api-1.7.7.jar"
        dependencies += ":code/lib/commons-cli-1.5.0.jar:code/lib/jsr166e-1.0.0.jar"
        dependencies += ":code/lib/args4j-2.33.jar:code/lib/native-lib-loader-2.1.3.jar"
        dependencies += ":code/lib/logback-classic-1.1.3.jar:code/lib/logback-core-1.1.3.jar"
        dependencies += ":code/lib/scala-library-2.11.8.jar:code/lib/scala-logging_2.11-3.1.0.jar"
        dependencies += ":code/lib/util-core_2.11-6.33.0.jar:code/lib/util-function_2.11-6.33.0.jar"
        dependencies += ":code/lib/pm-core-0.0.20-SNAPSHOT.jar:code/lib/pm-wrappers-0.0.20-SNAPSHOT.jar"
        dependencies += ":code/lib/scala-parser-combinators_2.11-1.0.4.jar:code/lib/scala-reflect-2.11.1.jar"
        #dependencies += ":code/lib/weightgen-0.0.3-SNAPSHOT.jar:code/lib/weightgen-logging-0.0.3-SNAPSHOT.jar"
        dependencies += ":code/lib/weightgen-0.0.5-SNAPSHOT.jar:code/lib/weightgen-logging-0.0.5-SNAPSHOT.jar"
        dependencies += ":code/lib/wg4ps-core-0.0.2-SNAPSHOT.jar:code/lib/flexics-core-0.0.1-SNAPSHOT.jar"
        dependencies += ":code/lib/m4ri-0.0.2-SNAPSHOT.jar:code/lib/eclat-0.0.2-SNAPSHOT.jar"
        #dependencies += ":code/lib/choco-solver-4.0.3.jar:code/lib/ampl-2.0.4.0.jar"
        #
        dependencies += ""
        #-------------------------------------------------------------------
        # $APP_HOME/code/lib/scala-library-2.11.8.jar:$APP_HOME/code/lib/pm-core-0.0.20-SNAPSHOT.jar:$APP_HOME/code/lib/weightgen-2.0.1-SNAPSHOT_XP.jar:$APP_HOME/code/lib/weightgen-logging-2.0.1-SNAPSHOT_XP.jar:$APP_HOME/code/lib/gflexics-0.0.3-SNAPSHOT.jar:$APP_HOME/code/lib/eclat-0.0.2-SNAPSHOT.jar:$APP_HOME/code/lib/util-core_2.11-6.33.0.jar:$APP_HOME/code/lib/pm-wrappers-0.0.20-SNAPSHOT.jar:$APP_HOME/code/lib/logback-classic-1.1.3.jar:$APP_HOME/code/lib/scala-logging_2.11-3.1.0.jar:$APP_HOME/code/lib/util-function_2.11-6.33.0.jar:$APP_HOME/code/lib/jsr166e-1.0.0.jar:$APP_HOME/code/lib/scala-parser-combinators_2.11-1.0.4.jar:$APP_HOME/code/lib/logback-core-1.1.3.jar:$APP_HOME/code/lib/slf4j-api-1.7.7.jar:$APP_HOME/code/lib/scala-reflect-2.11.1.jar:$APP_HOME/code/lib/native-lib-loader-2.1.3.jar:$APP_HOME/code/lib/pf4cs-1.0.5.jar:$APP_HOME/code/lib/choco-solver-4.0.3.jar:$APP_HOME/code/lib/args4j-2.33.jar:$APP_HOME/code/lib/commons-cli-1.5.0.jar:$APP_HOME/code/flexics-core/build/libs/flexics-core-1.0.1-SNAPSHOT_XP.jar:$APP_HOME/code/eflexics/build/libs/eflexics-1.0.1-SNAPSHOT_XP.jar:$APP_HOME/code/m4ri/build/libs/m4ri-1.0.1-SNAPSHOT_XP.jar:$APP_HOME/code/cp4im/build/libs/cp4im-1.0.1-SNAPSHOT_XP.jar:$APP_HOME/code/cdflexics/build/libs/cdflexics-1.0.1-SNAPSHOT_XP.jar:$APP_HOME/code/cldiv4im/build/libs/cldiv4im-1.0.1-SNAPSHOT_XP.jar:$APP_HOME/code/lewits/build/libs/lewits-1.0.1-SNAPSHOT_XP.jar:$APP_HOME/code/scd/build/libs/scd-1.0.1-SNAPSHOT_XP.jar
        #
        # command for execution
        argument = f"-d {data_file} -FI {data_file} -f {str(minsup)} -m {method} -F {features} -k {str(querySize)} -o {oracle} "
        argument += f"-l {str(queryRetention)} -r {ranker} -i {str(nb_iterations)} -t {str(tilt)} -s {seed_} -e {str(eta)} -FU ALL"
        if method == "dispale":
            argument += f" -a {aggreg}"
        #
        command = f"/usr/bin/timeout {timeLimit} /usr/bin/time -v -o {time_file} java -classpath {dependencies} "
        command += f"fr.phdhien.dispale.Main {argument} > {out_file}"
        #
        logs_ = open(logs_file, "w")
        subprocess.run([command + "; echo \" \nExit status: $?\" "], shell=True, check=True, stdout=logs_)
        #
    finally:
        queue.put(mp.current_process().name)
#
#=====================================================================================
#=====================================================================================
#
if __name__ == '__main__':
    # set the root directory
    cwd = os.path.dirname(os.path.realpath(__file__)) # current directory
    root_dir = os.path.realpath(os.path.join(cwd, os.pardir)) # root directory
    results_dir = os.path.realpath(os.path.join(root_dir, "results"))
    data_dir = os.path.realpath(os.path.join(root_dir, "data"))
    #
    #-------------------------------------------------------------------
    #
    # prepare the different configurations
    configs = []
    for dataname in all_dataname:
        dataset = dataname+".txt"
        if dataset in thresholds:
            data_file = os.path.join(data_dir, dataset)
            nbT = nbrTrans(data_file) # nb of transactions
            for freq in thresholds[dataset]:
                minsup = int( math.ceil( freq * nbT ) )
                for method in all_methods_to_launch:
                    c = get_method_config(method)
                    for ranker in all_rank_functions:
                        for features in all_features:
                            for querySize in all_querySizes:
                                for query_retention in all_query_retentions:
                                    for i in range(nb_repetitions):
                                        conf = (i+1, timeout, dataname, data_file, freq, minsup, )
                                        conf += (method, features, querySize, query_retention, ranker) + c
                                        configs.append(conf)
    #
    #-------------------------------------------------------------------
    #
    # launch processes one after the other, nbP processes can be launched in parallel
    curr = 0
    procs = dict()
    queue = mp.Queue() # queue of ongoing process
    for i in range(0, nbP):
        if curr < len(configs):
            proc_name = configs[curr][2] + " * " + str(configs[curr][4]) + " * " + str(configs[curr][6])
            proc_name += " * " + str(configs[curr][7]) + " * " + str(configs[curr][8])
            proc = Process(name=proc_name, target=run_dispale, args=(queue, configs[curr], root_dir, results_dir, ))
            proc.start()
            procs[proc.name] = proc
            curr += 1
    # using the queue, launch a new process whenever an old process finishes its workload
    while procs:
        name = queue.get()
        proc = procs[name]
        print(proc) 
        proc.join()
        del procs[name]
        if curr < len(configs):
            proc_name = configs[curr][2] + " * " + str(configs[curr][4]) + " * " + str(configs[curr][6])
            proc_name += " * " + str(configs[curr][7]) + " * " + str(configs[curr][8])
            proc = Process(name=proc_name, target=run_dispale, args=(queue, configs[curr], root_dir, results_dir, ))
            proc.start()
            procs[proc.name] = proc
            curr += 1
    #
    print("\n***** FINISHED *****\n")
    
    
    
    
    
    #########################################################################################################################################################

