'''
This file is part of the DiSPaLe project (https://gitlab.com/phdhien/dispale)

Copyright (c) 2023, IMT Atlantique and Normandie Université, France

Licensed under the MIT license.

 See LICENSE file in the project root for full license information.
'''

__author__ = "Maxime Garfagni"
__copyright__ = "Copyright (C) 2023 IMT Atlantique and Normandie Université"
__license__ = "MIT license"
#__version__ = "1.0"


import time
import sys
import os
import argparse
import heapq
from random import random
cwd=os.getcwd()
nb_items=None

###########################################################################
#                        Elementary functions                             #
###########################################################################

class wItem():
    def __init__(self, item, wpos, wneg):
        self.item = item
        self.wpos = wpos
        self.wneg = wneg
        self.wt = ""
        


def combin(n, k):
    if k>n: return 0
    if k==0 and n==0: return 1
    if k > n//2:
        k = n-k
    x = 1
    y = 1
    i = n-k+1
    while i <= n:
        x = (x*i)//y
        y += 1
        i += 1
    return x

    
def find(tab,i,j,x):
    m=int((i+j)/2)
    if m==0 or (tab[m-1]<x and x<=tab[m]):
        return m
    if tab[m]<x:
        return find(tab,m+1,j,x)
    return find(tab,i,m,x)


def computeCnk(j, l, tabCnk, M):
    if j+1>= len(tabCnk):
        for n in range(len(tabCnk), j+2):
            tabCnk.append([combin(n, k) for k in range(M+2)])
    return tabCnk[j][l]
    
def cover(dataset,pattern):
    cover=[]
    with open(dataset,'r') as base:
        line = base.readline()
        t=0
        while line:
            if (line=="" or line[0] == '#' or line[0] == '%' or line[0] == '@' or line.split(" ")==['\n']):
                line = base.readline()
                continue
            trans=line.split(" ")
            trans=trans[:len(trans)-1]
            i=0
            j=0
            while i<len(trans) and j<len(pattern) and int(trans[i])<=int(pattern[j]):
                if int(trans[i])==pattern[j]:
                    j+=1
                else:
                    i+=1
            if j==len(pattern):
                cover.append(t)
            line = base.readline()
            t+=1
    return cover
    
    
def utility(itemset,cover,w):
    u=0
    for item in itemset:
        u+=w[item]
    if nb_items==len(w):
        u=u*len(cover)
    else:
        s=0
        for t in cover:
            s+=w[nb_items+t]
        u=u*s
    return u

###########################################################################
#                        top k priority queue                             #
###########################################################################

def topK(sampledPatterns,k,covers,w):
    structure=[]
    for i in range(len(sampledPatterns)):
        heapq.heappush(structure,(utility(sampledPatterns[i],covers[i],w),i))
        if len(structure)>k:
            heapq.heappop(structure)
    return structure
    
###########################################################################
#                             ordering top k                              #
###########################################################################

def orderTopK(topK):
    if len(topK)<=1:
        return topK
    else:
        pivot=topK[0]
        inf=[]
        sup=[]
        for i in topK[1:]:
            if i[0]<pivot[0]:
                inf.append(i)
            else:
                sup.append(i)
    return orderTopK(sup)+[pivot]+orderTopK(inf)
            
###########################################################################
#                        Weighting algorithm                              #
###########################################################################

def weightDatasetDispale(dataset, M, tabCnk,w):
    wDatabase = []
    weights=[]
    z = 0
    t=0
    with open(dataset, 'r') as base:
        line = base.readline()
        while line:
            if (line=="" or line[0] == '#' or line[0] == '%' or line[0] == '@' or line.split(" ")==['\n']):
                line = base.readline()
                continue
            trans = line.split(" ")
            trans=trans[:len(trans)-1]
            #print(trans)
            wTrans = []
            i = len(trans)-1
            j=1
            while i >= 0:
                info = trans[i]
                #print(info)
                if j==1:
                    wTrans.append(wItem(info, [w[int(info)]], [0]))
                else:
                    wpos, wneg = [], []
                    for l in range(1, min(j,M)+1):
                        if l==1:
                            wpos.append(w[int(info)])
                        else:
                            wpos.append(w[int(info)]*computeCnk(j-1, l-1, tabCnk, M) + wTrans[0].wpos[l-2] + wTrans[0].wneg[l-2])
                        if l < len(wTrans[0].wpos)+1:
                            wneg.append(wTrans[0].wpos[l-1] + wTrans[0].wneg[l-1])
                    wTrans = [wItem(info, wpos, wneg+[0]*(len(wpos)-len(wneg)))] + wTrans            
                if i==0:
                    wt, x = [], 0
                    for l in range(len(wTrans[0].wpos)):
                        x += (wTrans[0].wpos[l] + wTrans[0].wneg[l])/(l+1)
                        wt.append(x)
                    wTrans[0].wt = wt 
                    if nb_items==len(w): 
                        z += wt[-1] 
                    else:
                        z += wt[-1] * w[nb_items+t]
                i -= 1
                j += 1
            wDatabase.append(wTrans)
            weights.append(z)
            t+=1
            line=base.readline()
        base.close()
        del base
    return wDatabase, weights

def weightDatasetBasic(dataset, delim, M, tabCnk):
    wDatabase = []
    weights = []
    z = 0
    with open(dataset, 'r') as base:
        line=base.readline()
        while line:
            trans = line.replace(" \n","").split(" ")
            wTrans = []
            i = len(trans)-1
            j=1
            while i >= 0:
                info = trans[i].split(delim)
                if j==1:
                    wTrans.append(wItem(info[0], [float(info[1])], [0]))
                else:
                    wpos, wneg = [], []
                    for l in range(1, min(j,M)+1):
                        if l==1:
                            wpos.append(float(info[1]))
                        else:
                            wpos.append(float(info[1])*computeCnk(j-1, l-1, tabCnk, M) + wTrans[0].wpos[l-2] + wTrans[0].wneg[l-2])
                        if l < len(wTrans[0].wpos)+1:
                            wneg.append(wTrans[0].wpos[l-1] + wTrans[0].wneg[l-1])
                    wTrans = [wItem(info[0], wpos, wneg+[0]*(len(wpos)-len(wneg)))] + wTrans            
                if i==0:
                    wt, x = [], 0
                    for l in range(len(wTrans[0].wpos)):
                        x += (wTrans[0].wpos[l] + wTrans[0].wneg[l])/(l+1)
                        wt.append(x)
                    wTrans[0].wt = wt  
                    z += wt[-1] 
                i -= 1
                j += 1
            wDatabase.append(wTrans)
            weights.append(z)
            line=base.readline()
        base.close()
        del base
    return wDatabase, weights

###########################################################################
#                        Sampling algorithm                               #
###########################################################################

def HAISampler(database, weights):
    xt = random()*weights[-1]
    i = find(weights,0,len(weights),xt)
    trans = database[i]
    xl = random()*trans[0].wt[-1]
    l = find(trans[0].wt,0,len(trans[0].wt),xl)+1
    j = len(trans)
    p=1
    y = 0
    pattern = []
    l1=l
    while l>0:
        d = random()*(y*tabCnk[j][l] + trans[p-1].wpos[l-1]+ trans[p-1].wneg[l-1])
        b = y*tabCnk[j-1][l-1] + trans[p-1].wpos[l-1]
        if d <= b:
            pattern.append(int(trans[p-1].item))
            y += trans[p-1].wpos[0]
            l -= 1
        p += 1
        j -= 1
    return pattern

###########################################################################
#               Some statistics of the sample                         #
###########################################################################

def printStats(dataset, sampledPatterns, M, delim):
    print("************* Begin statistics printing... *****************")
    data = []
    with open(dataset, 'r') as base:
            line=base.readline()
            while line:
                row = line.replace(" \n","").replace("\n","").split(" ")
                itemset = {}
                for itemUtil in row:
                    itemUtil = itemUtil.split(delim)
                    itemset[itemUtil[0]]= float(itemUtil[1])
                data.append(itemset)
                line=base.readline()
    base.close()
    del base
    statSample = {}
    for pattern in sampledPatterns:
        patt = set(pattern.replace('[','').replace(']','').replace("'","").split(", "))
        utilPattern = 0             
        for trans in data:
            trans1 = set(trans.keys())
            if patt <= trans1:
                util = 0
                for item in patt:
                    util += trans[item]
                utilPattern += util
        statSample[str(pattern).replace('[','').replace(']','').replace(',','')]=(sampledPatterns[pattern],utilPattern/len(patt),len(patt))
    with open(cwd+"/"+name+"_"+str(M)+"stats.txt", 'w') as outputStat:
        outputStat.write("Pattern\t&\tFrequency in the sample\t&\tAverage-Utility\t&\tLength\n")
        for patt in statSample:
            outputStat.write(patt+"\t"+str(statSample[patt][0])+"\t"+str(statSample[patt][1])+"\t"+str(statSample[patt][2])+"\n")
    outputStat.close()
    print("**************** End statistics printing! ********************\n")

###########################################################################
#                           Record the sample                             #
###########################################################################

def printSample(sampledPatterns,covers,finalTop, name, dataset):
    print("*********************** Recording... **************************")
    
    with open(cwd+"/"+name+".txt", 'w') as output:
        for element in finalTop:
            i=element[1]
            pattern=sampledPatterns[i]
            cover=covers[i]
            writtenPattern=str(pattern[0])
            writtenCover=str(cover[0])
            for item in pattern[1:]:
                writtenPattern=writtenPattern+" "+str(item)
            for transaction in cover[1:]:
                writtenCover=writtenCover+" "+str(transaction)
            output.write(writtenPattern+":"+writtenCover+"\n")
    output.close()
    print("********************* End recording! **************************\n")
    
    
###########################################################################
#                            main function                                #
###########################################################################

if __name__ == '__main__':
    print("#############################################################################")
    print("# Welcome to the HAISampler tool for high average utility itemset sampling! #")
    print("#############################################################################\n")
    parser=argparse.ArgumentParser()
    parser.add_argument('-dir','--directory')
    parser.add_argument('-run','--run')
    args=parser.parse_args()
    cwd=args.directory
    run_id = args.run
    parameters= cwd + f"/parameters-{run_id}.txt"
    with open(parameters, 'r') as param:
        nb_items=int(param.readline().replace('\n',""))
        dataset=param.readline().replace('\n',"")
        k=int(param.readline().replace('\n',""))  # The desired sample size
        M=int(param.readline().replace('\n',"")) # Maximum length constraint
        lastLine=param.readline().replace(' \n',"")
        w=lastLine.split(" ")
    
    #checking if the minimum weight is negative
    
    minWeight=float(w[0])
    for weight in w:
        if float(weight)<minWeight:
            minWeight=float(weight)
    newW=[]
    if minWeight<0:
        for weight in w:
            newW.append(float(weight)-minWeight)
    else:
        for weight in w:
            newW.append(float(weight))
    
    
    param.close()
    delim = ":" # Delimits an item with its utility in a transaction        
    beginTime = time.time()
    tabCnk = []          
    #database, weights = weightDatasetBasic(dataset, delim, M, tabCnk)
    database, weights = weightDatasetDispale(dataset, M, tabCnk,newW)
    endTime = time.time() - beginTime
    print("\tPreprocessing time (s)   :",endTime)
        
    sampledPatterns = []
    covers=[]
    beginTime = time.time()  
    while len(sampledPatterns)<100:
        pattern = HAISampler(database, weights)
        if pattern not in sampledPatterns:
            sampledPatterns.append(pattern)
            covers.append(cover(dataset,pattern))
    finalTop=orderTopK(topK(sampledPatterns,k,covers,newW))
    endTime = time.time() - beginTime
    print("\tSampling time (s)        :",endTime)
    print("\tDistinct sampled patterns:",len(sampledPatterns))
    del tabCnk
    printSample(sampledPatterns,covers,finalTop, f"topK-{run_id}", dataset)
    #printStats(dataset, sampledPatterns, M, delim) # Time consuming because compute the average utility of each drawn pattern
    del sampledPatterns
 
    
    print("#############################################################################")
    print("#                        Thank for using HAISampler!                        #")
    print("#############################################################################\n")

        
            
