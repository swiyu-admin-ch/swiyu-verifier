package ch.admin.bj.swiyu.verifier.service.statuslist;

public class StatusListFetchFailedException extends RuntimeException {
    public StatusListFetchFailedException(String message) {
        super(message);
    }
}