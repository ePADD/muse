package edu.stanford.muse.test;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

import java.io.File;

public class RandomCodeGen {
	public static void main(String[] args) throws IOException {
		//IMPORTANT NOTE TO CODE REFRESHERS. IF THE CODES FILE IS EMPTY, FOR THE FIRST USE, YOU WILL NEED TO MANUALLY CLEAR THE EMPTY SPACE AT THE TOP OF THE FILE.
		final int NUMBERTOADD = 1000; //how many codes do you wish to add?
		Random generator = new Random();
		File file = new File("C:/Users/Ankit Mathur/workspace/muse/src/java/edu/stanford/muse/xword/codes.txt");
		BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));
		for (int i =0;i < NUMBERTOADD; i++){
			int r = 1000000 + (int) (generator.nextFloat() * 8999900);
			String randomnum = String.valueOf(r);
			if (i == 0)
				writer.write("\n");
			writer.write(randomnum);
			if (i != (NUMBERTOADD-1))
				writer.write("\n");
			System.out.println("Posted code:" + r);
		}
		writer.close();
		// TODO Auto-generated method stub
	}
}