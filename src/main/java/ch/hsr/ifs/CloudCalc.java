package ch.hsr.ifs;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.IntStream;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;

public class CloudCalc {

	static class Job {
		public Job(int noMsgs, int noBytesPerMsg) {
			this.noMsgs = noMsgs;
			this.noBytesPerMsg = noBytesPerMsg;
		}

		public final int noMsgs;
		public final int noBytesPerMsg;
	}

	public static void main(String[] args) {
		String serverAddr = null;
		String clientHost = null;
		List<Job> jobs = new LinkedList<Job>();

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-s")) {
				serverAddr = args[i + 1];
				i++;
			} else if (args[i].equals("-c")) {
				clientHost = args[i + 1];
				i++;
			} else {
				String[] parts = args[i].split("\\*");
				jobs.add(new Job(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
			}

		}

		ZMQ.Context context = ZMQ.context(1);

		if (serverAddr != null) {
			new Server(context, serverAddr).start();
			// delay client creation to ensure server is ready when client sends first request
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		if (clientHost != null) {
			for (Job job : jobs) {
				Client client = new Client(context, clientHost, job.noMsgs, job.noBytesPerMsg);
				client.start();
				try {
					client.join();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	static class Server extends Thread {
		private final Context context;
		private final String addr;

		public Server(Context context, String addr) {
			this.context = context;
			this.addr = addr;
		}

		@Override
		public void run() {
			System.out.println("Bind server to " + addr);
			// Socket to talk to clients
			ZMQ.Socket responder = context.socket(ZMQ.REP);
			responder.bind(addr);

			byte sum = 0;

			while (!Thread.currentThread().isInterrupted()) {
				// Wait for next request from the client
				byte[] request = responder.recv(0);

				for (int i = 0; i < request.length; i++) {
					sum += request[i];
				}

				byte[] reply = new byte[]{ sum };

				responder.send(reply, 0);
			}
			responder.close();
		}
	}

	static class Client extends Thread {
		private final Context context;
		private final String addr;
		private final int noMsgs;
		private final int noBytesPerMsg;

		public Client(Context context, String addr, int noMsgs, int noBytesPerMsg) {
			this.context = context;
			this.addr = addr;
			this.noMsgs = noMsgs;
			this.noBytesPerMsg = noBytesPerMsg;
		}
		
		byte lastResult = 0;
		long counter = 0;

		@Override
		public void run() {
			// Socket to talk to server
			ZMQ.Socket requester = context.socket(ZMQ.REQ);
			requester.connect(addr);

			Duration dur = duration(() -> {
				IntStream.range(0, noMsgs).forEach(requestNbr -> {
					byte[] request = new byte[noBytesPerMsg];

					IntStream.range(0, noBytesPerMsg).forEach(byteNbr -> {
						request[byteNbr] = (byte) counter;
						counter += 1;
					});

					requester.send(request, 0);

					byte[] resp = requester.recv(0);
					
					lastResult = resp[0];
				});
			});

			System.out.println("host: " + addr + ", noMsgs: " + noMsgs + ", bytesPerMsg: " + noBytesPerMsg
					+ ", totalBytes: " + noMsgs * noBytesPerMsg + ", lastRes: " + lastResult + ", t: " + dur);

			requester.close();
		}
	}

	static Duration duration(Runnable f) {
		Instant start = Instant.now();
		f.run();
		Instant end = Instant.now();
		return Duration.between(start, end);
	}
}
