/*
 * Created on Oct 27, 2012
 *
 * Rutgers University, Department of Electrical and Computer Engineering
 * <P> Copyright (c) 2005-2012 Rutgers University
 */
package sime.tcp;

import sime.Simulator;

/**
 * This class defines how a TCP sender behaves in
 * the congestion avoidance state.<BR>
 * There are some subtleties in the actual TCP standard
 * that are not implemented here. For example,
 * it is recommended that the sender increments its
 * congestion window by one MSS per RTT, unless
 * the receiver acknowledged less than one MSS
 * during this period. This is to avoid security attacks
 * by so-called "ACK Division".
 * The reader should check the details in
 * <a href="http://tools.ietf.org/html/rfc5681" target="page">RFC 5681</a>.
 * 
 * @author Ivan Marsic
 *
 */
public class SenderStateCongestionAvoidanceVegas extends SenderState {

    /**
     * Constructor for the congestion avoidance state of a TCP sender.
     *
     * @param sender
     * @param slowStartState Slow start state
     * @param after3xDupACKstate State to enter after three duplicate-ACKs are received (different for Tahoe vs. Reno)
     */
    public SenderStateCongestionAvoidanceVegas(
    	SenderVegas sender, SenderState slowStartState, SenderState after3xDupACKstate
    ) {
    	this.sender = sender;
    	this.slowStartState = slowStartState;
    	this.congestionAvoidanceState = this;	// itself the congestion avoidance state
    	this.after3xDupACKstate = after3xDupACKstate;
     }

	/**
	 * The reason for this method is that the constructors
	 * {@link SenderStateCongestionAvoidanceVegas} and {@link SenderStateFastRecovery}
	 * need each other, so one has to be created first, and then
	 * the other will be set using this method.<BR>
	 * Thus package visibility only.
	 * 
	 * @param after3xDupACKstate the after3xDupACKstate to set
	 */
	void setAfter3xDupACKstate(SenderState after3xDupACKstate) {
		this.after3xDupACKstate = after3xDupACKstate;
	}

	/**
	 * Helper method to calculate the new value of the congestion
	 * window after a "new ACK" is received that acknowledges
	 * data never acknowledged before.<br />
	 * This method also resets the RTO timer for any outstanding segments.</p>
	 * 
	 * <p><a href="http://tools.ietf.org/html/rfc5681" target="page">RFC 5681</a>
	 * says that during congestion avoidance, TCP sender must not
	 * increase its congestion window by more than MSS bytes per round-trip time (RTT).<p>
	 * 
	 * <p>RFC 5681 describes several ways of how this can be achieved.
	 * The recommended way to increase CongWin during congestion avoidance is
	 * to count the number of bytes that have been acknowledged by ACKs for
	 * new data. When the number of bytes acknowledged reaches CongWin,
	 * then CongWin can be incremented by up to MSS bytes.</p>
	 * 
	 * <p>Another common method is to use the formula:
	 * <pre>
	 * CongWin += MSS*MSS/CongWin
	 * </pre>
	 * Note that for a connection where the receiver sends cumulative
	 * ACKs, this formula will lead to increasing CongWin by less
	 * than 1 full-sized segment per RTT.<p>
	 * 
	 * @param ackSequenceNumber_ acknowledged data sequence number
	 * @param lastByteAcked_ last byte previously acknowledged (not yet updated with this new ACK!)
	 * @return the new value of the congestion window
	 */
	@Override
	protected int calcCongWinAfterNewAck(
		int ackSequenceNumber_, int lastByteAcked_
	) {
    	// Re-start the RTO timer for any outstanding segments.
		if (sender.lastByteAcked < sender.lastByteSent) {
			sender.startRTOtimer();
		} else { // everything is ACK-ed, cancel the RTO timer
			sender.cancelRTOtimer();
		}

		int congWindowNew_ = sender.congWindow;
		System.out.println("ackSequenceNumber = "+ackSequenceNumber_+" \t\t\t\t\t\t\tand lastByteAcked :"+lastByteAcked_+" Difference of :"+((ackSequenceNumber_-lastByteAcked_)/Sender.MSS));
		System.out.println("Congestion Window :"+congWindowNew_/Sender.MSS);
		sender.expectedThroughput=sender.congWindow/Sender.MSS;
		sender.actualThroughput=(ackSequenceNumber_-lastByteAcked_)/Sender.MSS;
		sender.diffThroughput=sender.expectedThroughput-sender.actualThroughput;
		System.out.println("sender.diffThroughput:"+sender.diffThroughput);



		// TCP Vegas - check expected betwee alpha and beta
		if (sender.diffThroughput <=sender.alpha) {
			congWindowNew_ += Sender.MSS;
		} else if(sender.diffThroughput<=sender.beta) {
			congWindowNew_ -= Sender.MSS;
		}
		else{
			//do nothing
		}
		return congWindowNew_;
	}

	/**
	 * Helper method to look-up the next state
	 * that the sender will transition to after this one.
	 * 
	 * @return the next state to transition to.
	 */
	@Override
	protected SenderState lookupNextStateAfterNewAck() {
    	// Check if the congestion window fell below the slow-start-threshold;
		// If YES, change the sender's mode to "slow start"
    	if (sender.congWindow < sender.SSThresh) {
    		if (	// this can never happen, but just in case ...
        			(Simulator.currentReportingLevel & Simulator.REPORTING_SENDERS) != 0
    			) {
    				System.out.println("############## Sender entering slow start.");
    			}
    		sender.resetParametersToSlowStart();
    		return slowStartState;	// transition to the slow start state
    	} else {
    		return this;	// remain in the congestion avoidance state
    	}
	}
}