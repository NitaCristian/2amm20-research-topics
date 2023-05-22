package fr.phdhien.dispale;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class ParseFlexicsSample {
	public int nbFold;
	public int nb_patterns_per_fold;
	public int currentFold;
	public int currentPosition;
	public String resFilePath;
	public Set<Integer> currentCover;
	public Set<Integer> currentItemset;
	public HashSet<Integer>[] all_patterns;
	//public Map<Set<Integer>, Set<Integer>> all_patterns_and_covers;
	public Map<Integer, Set<Integer>> all_patterns_map;
	public Map<Set<Integer>, Set<Integer>> all_covers_map;
	
	public Set<Integer>[][] patterns_per_fold;
	
	public int fold_iterator; // iterate over test list
	public int index_iterator; // permit to know on which list (training or test) we iterate
	
	public ParseFlexicsSample(int nbFold, int nb_patterns_per_fold, String resFile) {
		this.nbFold = nbFold;
		this.nb_patterns_per_fold = nb_patterns_per_fold;
		this.currentFold = 0;
		this.currentPosition = 0;
		this.resFilePath = resFile;
		this.currentCover = new HashSet<Integer>();
		this.currentItemset = new HashSet<Integer>();
		this.fold_iterator = 0;
		this.index_iterator = 0;
	}
	
	public void readResultsFile() {
		File f = new File(resFilePath);
		if(!f.exists()) {
			System.out.println("Fichier Flexics inexistant");
			System.exit(1);
		}
		
		BufferedReader br;
		//all_patterns_and_covers = new HashMap<Set<Integer>, Set<Integer>>();
		all_patterns_map = new HashMap<Integer, Set<Integer>>();
		all_covers_map = new HashMap<Set<Integer>, Set<Integer>>();
		
		try {
			int it = 0;
			String line = "";
			br = new BufferedReader(new FileReader(resFilePath));
			while ((line = br.readLine()) != null) {
				if (line.equals("[EOF]"))
					break;
				// if the line is a comment, is empty or is metadata
				if (line.isEmpty() == true || line.charAt(0) == '#' 
						|| line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}
				
				//String[] elements = line.strip().split("] [");
				//String[] elements = line.trim().split("] [");
				String[] elements = line.trim().split("]");
				if(elements.length < 2)
					continue;
				
				//elements[0] = elements[0].replace("[", "").strip(); // pattern
				//elements[1] = elements[1].replace("]", "").strip(); // cover
				elements[0] = elements[0].replace("[", "").trim(); // pattern
				elements[1] = elements[1].replace("[", "").trim(); // cover
				String[] pattern = elements[0].split(" "), cover = elements[1].split(" ");
				
				Set<Integer> p = new HashSet<Integer>(), c = new HashSet<Integer>();
				for(int i=0; i<pattern.length; i++)
					p.add(Integer.parseInt(pattern[i])-1);
				for(int i=0; i<cover.length; i++)
					c.add(Integer.parseInt(cover[i])-1);
				
				//all_patterns_and_covers.put(p, c);
				all_patterns_map.put(it, p);
				all_covers_map.put(p, c);
				
				it++;
				//System.out.println("#ligne " + it + " --> " + elements[0] + "\n" + p + "\n");
			}
			br.close();
			
			//System.out.println("\n\n***************************\n***************************\n\n");
			//all_patterns = all_patterns_and_covers.keySet().toArray(
			//		new HashSet[all_patterns_and_covers.size()]);
			all_patterns = all_patterns_map.values().toArray(
					new HashSet[all_patterns_map.size()]);
			
			gather_in_folds();
			
			/*
			it=0;
			for(int i=0; i<nbFold; i++) {
				for(int j=0; j<nb_patterns_per_fold; j++) {
					it++;
					System.out.println("" + it + "-" + i + "*" + j + " ==> " + patterns_per_fold[i][j]);
				}
			}
			System.out.println("\n\n***************************\n***************************\n\n");
			//*/
			
			//System.out.println("--- Fin de lecture ---\n\n\n");
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("Problème avec le Fichier res Flexics");
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	private void gather_in_folds() {
		patterns_per_fold = new Set[nbFold][nb_patterns_per_fold];
		
		System.out.println("nbFold = " + nbFold + ", nb_patterns_per_fold = " + nb_patterns_per_fold + ", |all_patterns| = " + all_patterns.length + "\n");
		
		for(int i=0; i<nbFold; i++) {
			for(int j=0; j<nb_patterns_per_fold; j++) {
				patterns_per_fold[i][j] = all_patterns[(i*nb_patterns_per_fold) + j];
			}
		}
		//System.out.println("\n--- End gather_in_folds ---");
	}
	
	public void next_fold() {
		currentFold++;
		fold_iterator = 0;
		currentPosition = 0;
		currentCover.clear();
		currentItemset.clear();
		//System.out.println("\n---\nNEXT FOLD\n---");
		
		//System.out.println("\n\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n");
		
	}
	
	public boolean unexplored_folds() {
		//next_fold();
		//System.out.println("\n---\nFold n° " + currentFold + "\nPosition n°" + currentPosition + "\n---");
		//System.out.println("\n\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n");
		return (currentFold < nbFold);
	}
	
	public boolean getNextSolution() {
		currentCover.clear();
		currentItemset.clear();
		
		if(index_iterator == 0) { // get training set patterns
			if(currentPosition >= nb_patterns_per_fold) {
				return false;
			}
			//currentItemset = patterns_per_fold[currentFold][currentPosition];
			Iterator<Integer> it1 = patterns_per_fold[currentFold][currentPosition].iterator();
			while(it1.hasNext())
				currentItemset.add(it1.next());
			
			//currentCover = (HashSet<Integer>) all_patterns_and_covers.get(currentItemset);
			//Iterator<Integer> it2 = all_patterns_and_covers.get(currentItemset).iterator();
			Iterator<Integer> it2 = all_covers_map.get(currentItemset).iterator();
			
			while(it2.hasNext())
				currentCover.add(it2.next());
			
			currentPosition++;
			
			//System.out.println("\n\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n");
			
			return true;
		}
		else { // get test set patterns
			if((fold_iterator == currentFold) || // avoid training set patterns
					(currentPosition >= nb_patterns_per_fold)) { // jump to next fold
				fold_iterator++;
				currentPosition = 0;
				return getNextSolution();
			}
			
			if(fold_iterator >= nbFold) {
				//System.out.println("\n\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n");
				return false;
			}
			else {
				//currentItemset = patterns_per_fold[fold_iterator][currentPosition];
				Iterator<Integer> it1 = patterns_per_fold[fold_iterator][currentPosition].iterator();
				while(it1.hasNext())
					currentItemset.add(it1.next());
				
				//currentCover = (HashSet<Integer>) all_patterns_and_covers.get(currentItemset);
				//Iterator<Integer> it2 = all_patterns_and_covers.get(currentItemset).iterator();
				Iterator<Integer> it2 = all_covers_map.get(currentItemset).iterator();
				while(it2.hasNext())
					currentCover.add(it2.next());
				
				currentPosition++;
				
				//System.out.println("\n\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n");
				
				return true;
			}
		}
	}
	
	public Set<Integer> getCurrentSolutionItems(){
		//System.out.println("Current Itemset : " + currentItemset);
		
		return currentItemset;
	}
	
	public Set<Integer> getCurrentSolutionCover() {
		return currentCover;
	}
	
	public void switch_iterator(int it) {
		//index_iterator = (index_iterator==0) ? 1 : 0;
		index_iterator = it;
		currentPosition = 0;
		currentCover.clear();
		currentItemset.clear();
	}
	
	public void reset() {
		currentFold = 0;
		currentPosition = 0;
		fold_iterator = 0;
		index_iterator = 0;
		currentCover.clear();
		currentItemset.clear();
		all_patterns = null;
		//all_patterns_and_covers.clear();
		all_patterns_map.clear();
		all_covers_map.clear();
	}
	
	
}

