package clearnet.android.help;

import annotations.RPCMethodScope;
import clearnet.interfaces.RequestCallback;
import io.reactivex.Observable;

public interface TestRequests {
    @RPCMethodScope("test")
    void requestWithCallback(RequestCallback<Object> requestCallback);

    @RPCMethodScope("test")
    Observable<Object> reactiveRequest();
}
