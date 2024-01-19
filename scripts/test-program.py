'''
Created on 23 Feb 2023

@author: Lobnury
'''

import os
import sys
import math
import argparse
import subprocess
#
nbP = 4 # number of project to build in parallel
timeout = "3600s" # or "86400s"
#
#---------------------------------------------------------------------------------
#---------------------------------------------------------------------------------
#
#all_dataname = ["foodmart", "retail"]
#all_dataname = ["BMS1", "chess", "connect", "heart-cleveland", "hepatitis", 
#                    "kr-vs-kp", "mushroom", "retail", "splice1", "T10I4D100K", "T40I10D100K"]
all_dataname = ["hepatitis"]
#
#---------------------------------------------
#
#all_dataname = ["chess"]
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
        "chess.txt":[0.3],
        "connect.txt":[0.3],
        "kr-vs-kp.txt":[0.3],
        "splice1.txt":[0.3],
        "foodmart.txt":[0.0]
}
#--------------------------------------------
#--------------------------------------------
thresholds_PARSING = {
        "chess.fimi" : [0.6],
        "foodmart.fimi": [0.0001],
        "mushroom.fimi" : [0.079],
        "lymph.fimi" : [0.32],
        "hepatitis.fimi" : [0.35],
        "heart-cleveland.fimi" : [0.388],
        "kr-vs-kp.fimi" : [0.63],
        "zoo-1.fimi" : [0.09],
        "german-credit.fimi" : [0.35],
        "vote.fimi" : [0.057],
        "soybean.fimi" : [0.044],
        "anneal.fimi" : [0.81],
}
#--------------------------------------------
#--------------------------------------------
nb_items = {
        "anneal" : 94,
        "chess": 76,
        "foodmart": 1559,
        "mushroom": 119,
        "splice1": 287,
        "hepatitis" : 68,
        "heart-cleveland" : 95,
        "german-credit" : 112,
        "kr-vs-kp" : 74,
        "lymph" : 68,
        "soybean" : 50,
        "vote" : 48,
        "zoo-1" : 36,
}
#
#--------------------------------------------------------
#--------------------------------------------------------
#
seed = 42085406528110384
#
#--------------------------------------------------------
#
all_methods_to_launch = ["letsip", "lutom", "dispale", "lutomDisc"]
freq_based_methods = ["dispale", "letsip"]
disc_methods = ["dispale", "lutomDisc"]
hui_methods = ["lutom", "lutomDisc"]
#
#--------------------------------------------------------
#
disc_aggregation = ["exp", "EXP", "LIN", "lin"] # Exponential and Linear
#
#--------------------------------------------------------
#
#all_oracles = ["EFlexics", "HUISampler", "TKUCE", "TKO"]
all_oracles = ["Flexics", "flexics", "HUIMiner", "huiminer"]
#
hui_algo = ["HAISampler", "haisampler", "TKUCE", "tkuce", "TKO", "tko"]
sampling_algo = ["EFlexics", "eflexics"]
all_algo = hui_algo + sampling_algo
#
#--------------------------------------------------------
#
all_rank_functions = ["FrequencyRanker", "SurprisingnessRanker", "GaussianRanker"]
#
#--------------------------------------------------------
#
all_features = ["Items", "Items-Transactions", "Items-Transactions-Frequency-Length"]
hui_features = ["Items", "Items-Transactions"]
features_abbrv = ["F", "I", "L", "T", "FI", "FL", "FT", "IL", "IT", "LT", "FIL", "FIT", "FLT", "ILT", "FILT"]
hui_features_abbrv = ["I", "IT"]
#
fUpdate = ["all", "ALL", "rnd", "RND"]
#
#=====================================================================================
#=====================================================================================
#
def create_folder(folder):
    os.makedirs(folder,exist_ok=True)
#
#=====================================================================================
#=====================================================================================
#
def nbrTrans(dataset_file):
    nbr_trans = 0
    if os.path.exists(dataset_file): 
        with open(dataset_file, "r") as infile:
            nbr_trans = 0
            for line in infile:
                if line.rstrip() and line[0] != '@':
                    nbr_trans += 1
    #
    return nbr_trans
#
#=====================================================================================
#=====================================================================================
#
def format_features(features):
    features_list = features.split("-")
    ft_list = []
    for ft in features_list:
        if ft.lower() == "items":
            ft_list.append("I")
        if ft.lower() == "transactions": 
            ft_list.append("T")
        if ft.lower() == "frequency": 
            ft_list.append("F")
        if ft.lower() == "length": 
            ft_list.append("L")
    ft_list.sort()
    ft_format = "".join(ft_list)
    return ft_format
#
#=====================================================================================
#=====================================================================================
#
def get_data_list():
    #
    cwd = os.path.dirname(os.path.realpath(__file__)) # current directory
    root_dir = os.path.realpath(os.path.join(cwd, os.pardir)) # root directory
    data_dir = os.path.realpath(os.path.join(root_dir, "data"))
    #
    data_list = list({ data.replace(".fimi", "").replace(".txt", "") for data in os.listdir(data_dir) })
    data_list.sort()
    data_list_str = ", ".join(data_list)
    #
    return data_list_str
#
#=====================================================================================
#=====================================================================================
#
def check_if_project_is_built(root_dir):
    """check the code directory to verify that the project has been built

    Args:
        root_dir (str): the root directory of the project

    Returns:
        bool: the build status of the code: True or False
    """
    code_dir = os.path.realpath(os.path.join(root_dir, "code"))
    dispale_dir = os.path.realpath(os.path.join(code_dir, "dispale", "build", "libs"))
    scd_dir = os.path.realpath(os.path.join(code_dir, "scd", "build", "libs"))
    #
    is_built = False
    if os.path.exists(dispale_dir) and os.path.exists(scd_dir):
        is_built = True
    #
    return is_built
#
#=====================================================================================
#=====================================================================================
#
def get_dependencies():
    """_summary_

    Returns:
        str: the list of all dependencies separated by ``:''
    """
    #
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
    dependencies += ":code/lib/weightgen-0.0.5-SNAPSHOT.jar:code/lib/weightgen-logging-0.0.5-SNAPSHOT.jar"
    dependencies += ":code/lib/wg4ps-core-0.0.2-SNAPSHOT.jar:code/lib/flexics-core-0.0.1-SNAPSHOT.jar"
    dependencies += ":code/lib/m4ri-0.0.2-SNAPSHOT.jar:code/lib/eclat-0.0.2-SNAPSHOT.jar"
    #
    return dependencies
#
#=====================================================================================
#=====================================================================================
#
def get_argParser():
    """Create a parser for command line arguments

    Returns:
        argparse.ArgumentParser: the parser object
    """
    #
    parser:argparse.ArgumentParser = argparse.ArgumentParser(
                                    prog="DiSPaLe Launcher",
                                    #prog=f"{os.path.basename(sys.argv[0])}",
                                    description="Process dispale, letsip and lutom arguments and prepare running.",
                                    epilog="Copyrights: Normandie Université and IMT Atlantique (Contact: Arnold Hien or Samir Loudni)\n\n")
    #
    parser.add_argument("--version", action="version", version="DiSPaLe 2.0")
    #
    parser.add_argument("-m", "--method", type=str, nargs=1, choices=all_methods_to_launch, required=True, help=f"the method to run: {all_methods_to_launch}")
    parser.add_argument("-d", "--data", type=str, nargs=1, required=True, help="the dataset name (check 'data' directory). The dataset file must respect CP4IM format (check `.txt' file in data directory)")
    parser.add_argument("-k", "--query", type=int, nargs=1, required=True, help="the query size, ie. #patterns mined at each iterations")
    parser.add_argument("-o", "--oracle", type=str, nargs=1, choices=all_oracles, required=True, help="the oracle approach used to extract patterns")
    parser.add_argument("-a", "--algo", type=str, nargs=1, choices=all_algo, required=True, help="the algorithm used to mine patterns")
    parser.add_argument("-F", "--features", type=str, nargs=1, required=True, help="Used to represent patterns and for learning ( items, transactions, length, ...)")
    parser.add_argument("-r", "--rank", type=str, nargs=1, choices=all_rank_functions, default="SurprisingnessRanker", help="the rank function emulating the user")
    parser.add_argument("-l", "--retention", type=int, nargs=1, default=0, help="#patterns of each iteration to keep for the next one (0 ≤ l < k)")
    parser.add_argument("-i", "--iter", type=int, nargs=1, required=True, help="#iterations of the interactive process")
    #
    parser.add_argument("-s", "--seed", type=int, nargs="?", default=seed, help="the the random seed")
    parser.add_argument("-t", "--tilt", type=float, nargs=1, default=10.0, help="tilt parameter for the weight function ")
    parser.add_argument("-e", "--eta", type=float, nargs=1, default=0, help=f"the regularization parameter of methods {disc_methods} ")
    parser.add_argument("-f", "--freq", type=float, default=0.5, help=f"the frequency used when 'method in {freq_based_methods}' ")
    parser.add_argument("-ag", "--aggregation", type=str, nargs=1, default="LIN", choices=disc_aggregation, help=f"the regularization parameter agregation function ")
    #
    parser.add_argument("-FU", "--featsUpdate", type=str, nargs=1, default="ALL", choices=fUpdate, help=f"define how the features weights should be updated ")
    #
    parser.add_argument("-w", "--weights", type=str, nargs=1, default="", help="the file containing the weights when rank=GaussianRanker ")
    #
    parser.add_argument("-to", "--timeout", type=int, default=3600, help="the time limit (in sec.) within which the program must run ")
    #
    #
    return parser
#
#=====================================================================================
#=====================================================================================
#
def parse_parameters():
    #
    parser = get_argParser()
    params = parser.parse_args()
    #
    #-------------------------------------------------------------------
    #
    method:str = params.method[0]
    data:str = params.data[0]
    feats:str = params.features[0]
    oracle:str = params.oracle[0]
    algo:str = params.algo[0]
    rankFunction:str = params.rank
    nbIter:int = params.iter[0]
    queryK:int = params.query[0]
    queryRetention:int = params.retention[0]
    eta:float = params.eta
    aggreg:str = params.aggregation
    featsUpdate:str = params.featsUpdate
    weightsFile:str = params.weights
    rndSeed:int = params.seed
    freq:float = params.freq
    tilt:float = params.tilt
    timeout:int = params.timeout
    #
    #-------------------------------------------------------------------
    #
    assert nbIter > 0, f"The number of iterations must be strictly positive."
    assert queryRetention in range(0, queryK), f"The query retention parameter must be in range [0, {queryK-1}]."
    assert format_features(feats) in features_abbrv, f"The features used must be in {all_features} or a combination of this list elements separated by `-'."
    assert (freq>0) and (freq<1), f"the minimum frequency value must be in range ]0, 1[."
    assert tilt in range(0, 100), f"The tilt value give the range of the weight function. It must be in range [0, 100[."
    #
    if method in hui_methods:
        assert oracle.lower() == "huiminer", f"When using HUI methods, the oracle must be `huiminer'."
        assert algo in hui_algo, f"When using HUI methods, the following algorithms can be selected: { ', '.join( list( set( map(lambda x: x.upper(), hui_algo) ) ) ) }"
        assert format_features(feats) in hui_features_abbrv, f"Current version only allow `Items' as features or the combination of { ' and '.join(hui_features)}."
    #
    if method in freq_based_methods:
        assert oracle.lower() == "flexics", f"Current version only allow `flexics' as the oracle of { ' and '.join( freq_based_methods ) }."
        assert algo.lower() in sampling_algo, f"Current version only allow `EFlexics' sampler for mining patterns."
        #
        if method.lower() == "dispale":
            assert eta*100 in range(0, 100), f"The regularization parameter must be in range [0, 1[."
    #
    #-------------------------------------------------------------------
    #
    parameters = (method, data, freq, feats, oracle.lower(), algo.lower(), rankFunction, nbIter, )
    parameters += (queryK, queryRetention, eta, aggreg, featsUpdate, tilt, weightsFile, rndSeed, timeout)
    
    print(parameters, "\n\n")
    #
    return parameters
#
#=====================================================================================
#=====================================================================================
#
def get_arguments():
    #
    # set the root directory
    cwd = os.path.dirname(os.path.realpath(__file__)) # current directory
    root_dir = os.path.realpath(os.path.join(cwd, os.pardir)) # root directory
    results_dir = os.path.realpath(os.path.join(root_dir, "results"))
    data_dir = os.path.realpath(os.path.join(root_dir, "data"))
    #
    #-------------------------------------------------------------------
    #
    parameters = parse_parameters()
    (method, dataname, freq, feats, oracle, algo, ranker, nbIter) = parameters[:8]
    (queryK, queryR, eta, aggreg, featsUpdate, tilt, weightsFile, rndSeed, timeout) = parameters[-9:]
    #
    #-------------------------------------------------------------------
    #
    dataset_cp4im = dataname+".txt"
    dataset_fimi = dataname+".fimi"
    data_file_fimi = os.path.join(data_dir, dataset_fimi)
    data_file_cp4im = os.path.join(data_dir, dataset_cp4im)
    #
    nbT = nbrTrans(data_file_fimi) # nb of transactions
    minsup = int( math.ceil( freq * nbT ) )
    #
    #-------------------------------------------------------------------
    #
    # command for execution
    arguments = f"-m {method} -o {oracle} -a {algo} -F {feats} -r {ranker} -k {queryK} -l {queryR} "
    arguments += f"-i {nbIter} -t {tilt} -ag {aggreg} -e {eta} -FU {featsUpdate} -s {rndSeed} -f {minsup} "
    arguments += f"-d {data_file_cp4im} -FI {data_file_fimi}"
    #
    if str(weightsFile):
        arguments += f" -w {weightsFile}"
    #
    return (arguments, timeout)
    #
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
    if not check_if_project_is_built(root_dir):
        print("\nDispale 2.0\n\nThe project code is not yet built. Use script build-code.py to build. \n")
    else:
        (prg_arg, timeout) = get_arguments()
        dependencies = get_dependencies()
        #
        #-------------------------------------------------------------------
        #
        command = f"java -classpath {dependencies} fr.phdhien.dispale.Main {prg_arg}"
        if timeout is not None:
            command = f"/usr/bin/timeout {timeout}s {command}"
        #
        proc = subprocess.run([command], shell=True, check=True)
        #subprocess.run([command + "; echo \" \nExit status: $?\" "], shell=True, check=True)
        #
        if proc.returncode == 0:
            print("\n\nExit status: 0")
        #
        #-------------------------------------------------------------------
        #
        print("\n***** FINISHED *****\n")
        #
    
    #####################################################################
    #####################################################################

