package fr.phdhien.dispale;

import java.io.BufferedReader;
//import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class RankSVMTools {
	public int features_count;
	public boolean ok = false;
	public String train_file;
	public String model_file;
	
	public RankSVMTools(int features_count, String train_file, String model_file) {
		this.features_count = features_count;
		this.train_file = train_file;
		this.model_file = model_file;
	}
	
	public void run_ranksvm_learning(){
		ok = false;
		try {
			String command = "./bin/svm_rank_learn -c 0.01 " + train_file + " " + model_file;
			Process p = Runtime.getRuntime().exec(command);
			p.waitFor();
			ok = true;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		//return ok;
	}
	
	public double[] read_model_file() {
		File f = new File(model_file);
		if(!f.exists()) {
			System.out.println("Fichier model_file inexistant");
			System.exit(1);
		}
		double[] w = new double[features_count];
		for(int i=0; i<features_count; i++){
			w[i] = -1000.0;
		}
		
		BufferedReader br;
		
		try {
			//int it = 0;
			String line = "";
			br = new BufferedReader(new FileReader(model_file));
			while ((line = br.readLine()) != null) {
				if (line.equals("[EOF]"))
					break;
				// if the line is a comment, is empty or is metadata
				if (line.isEmpty() == true || !line.contains(":")) {
					continue;
				}
				//
				String ligne = line.substring(2).replace("#", "").trim();
				
				/*
				System.out.println("ligne --> " + ligne);
				System.out.print("--> ");
				*/
				
				String[] tab_elem = ligne.split(" ");
				int n = tab_elem.length;
				for(int i=0; i<n; i++){
					int index = Integer.parseInt(tab_elem[i].split(":")[0]) - 1;
					w[index] = Double.parseDouble(tab_elem[i].split(":")[1]);
					//System.out.print("| " + index + " ");
				}
				//System.out.println("| \n\n");
				//
				ok = true;
				break;
				//
			}
			br.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("Problème avec le Fichier model_file");
			e.printStackTrace();
			System.exit(1);
		}
		
		return w;
	}
	
	public void save_train_data(String train_data) {
		//BufferedWriter bw;
		String[] all_lines = train_data.split("\n");
		
		try {
			//int it = 0;
			//String line = "";
			//bw = new BufferedWriter(new FileWriter(train_file, false));
			FileWriter fr = new FileWriter(train_file, false);
			for(int i=0; i<all_lines.length; i++){
				String s = all_lines[i] + "\n";
				fr.write(s);
			}
			//bw.close();
			fr.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("Problème avec le Fichier train_file");
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	
}
