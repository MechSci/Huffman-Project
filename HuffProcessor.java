import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 * @editor Ethan Yu
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD);
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[] freqs = determineFreqs(in);
		HuffNode trieRoot = encodingTrie(freqs);
		String[] cipher = new String[ALPH_SIZE + 1];
		mapCharToCode(trieRoot, cipher, "");
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(trieRoot, out);

		in.reset();
		int currentChunk = in.readBits(BITS_PER_WORD);
		String compressedChunk;
		while(currentChunk != -1){
			compressedChunk = cipher[currentChunk];
			out.writeBits(compressedChunk.length(), Integer.parseInt(compressedChunk, 2));
			currentChunk = in.readBits(BITS_PER_WORD);
		}
		compressedChunk = cipher[PSEUDO_EOF];
		out.writeBits(compressedChunk.length(), Integer.parseInt(compressedChunk, 2));
		out.close();
	}
	private int[] determineFreqs(BitInputStream in){
		int[] freqs = new int[ALPH_SIZE + 1];
		int currentChunk = in.readBits(BITS_PER_WORD);
		while(currentChunk != -1){
			freqs[currentChunk]++;
			currentChunk = in.readBits(BITS_PER_WORD);
		}
		freqs[PSEUDO_EOF] = 1;
		return freqs;
	}
	private HuffNode encodingTrie(int[] freqs){
		PriorityQueue<HuffNode> currentForest = new PriorityQueue<>();
		for(int i=0; i < freqs.length; i++){
			if(freqs[i] > 0){
				currentForest.add(new HuffNode(i, freqs[i], null, null));
			}
		}
		while(currentForest.size() > 1){
			HuffNode left = currentForest.remove();
			HuffNode right = currentForest.remove();
			HuffNode combined = new HuffNode(0, left.myWeight + right.myWeight, left, right);
			currentForest.add(combined);
		}
		HuffNode root = currentForest.remove();
		return root;
	}
	private void mapCharToCode(HuffNode trieRoot, String[] cipher, String path){
		if(trieRoot == null)
			return;
		if(trieRoot.myLeft == null && trieRoot.myRight == null){
			cipher[trieRoot.myValue] = path;
			return;
		}
		mapCharToCode(trieRoot.myLeft, cipher, path + "0");
		mapCharToCode(trieRoot.myRight, cipher, path + "1");
	}
	private void writeHeader(HuffNode trieRoot, BitOutputStream out){
		if(trieRoot == null){
			return;
		}
		if(trieRoot.myLeft == null && trieRoot.myRight == null){
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, trieRoot.myValue);
			return;
		}
			out.writeBits(1, 0);
			writeHeader(trieRoot.myLeft, out);
			writeHeader(trieRoot.myRight, out);
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		int current = in.readBits(BITS_PER_INT);
		if (current != HUFF_TREE) {
			throw new HuffException("invalid magic number " + current);
		}

		HuffNode root = readTree(in);
		HuffNode currentNode = root;
		while(true){
			int currentBit = in.readBits(1);
			if(currentBit == -1)
				throw new HuffException("bad input; no PSEUDO_EOF");
			else{
				if(currentBit == 0)
					currentNode = currentNode.myLeft;
				else
					currentNode = currentNode.myRight;
				if(currentNode.myLeft == null && currentNode.myRight == null){
					if(currentNode.myValue == PSEUDO_EOF)
						break;
					else{
						out.writeBits(BITS_PER_WORD, currentNode.myValue);
						currentNode = root;
					}
				}
			}
		}
		out.close();
	}
	private HuffNode readTree(BitInputStream in){
		int currentBit = in.readBits(1);
		if(currentBit == -1)
			throw new HuffException("bad input; incomplete beginning tree");
		if(currentBit == 0){
			HuffNode left = readTree(in);
			HuffNode right = readTree(in);
			return new HuffNode(0, 0, left, right);
		}
		else{
			int letter = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(letter, 0, null, null);
		}
	}
}