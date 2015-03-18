package org.opentripplanner.profile;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.model.Leg;
import org.opentripplanner.api.model.WalkStep;
import org.opentripplanner.api.parameter.QualifiedMode;
import org.opentripplanner.api.resource.CoordinateArrayListSequence;
import org.opentripplanner.api.resource.GraphPathToTripPlanConverter;
import org.opentripplanner.common.geometry.GeometryUtils;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.util.PolylineEncoder;
import org.opentripplanner.util.model.EncodedPolylineBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

/** 
 * A response object describing a non-transit part of an Option. This is either an access/egress leg of a transit
 * trip, or a direct path to the destination that does not use transit.
 */
public class StreetSegment {

    private static final Logger LOG = LoggerFactory.getLogger(StreetSegment.class);

    @JsonSerialize(using = ToStringSerializer.class) // as a string (e.g. "BICYCLE_RENT" instead of a nested object)
    public QualifiedMode qmode;
    public int time;
    public EncodedPolylineBean geometry;
    public List<WalkStep> walkSteps = Lists.newArrayList();
    public List<StreetEdgeInfo> streetEdges = Lists.newArrayList();

    /**
     * Build the walksteps from the final State of a path.
     * The path may contain more than one leg, because you may need to walk the bike.
     * Therefore accumulate the geometries into one long polyline, and accumulate the walksteps.
     */
    public StreetSegment (State state) {
        GraphPath path = new GraphPath(state, false);
        CoordinateArrayListSequence coordinates = new CoordinateArrayListSequence();
        for (Edge edge : path.edges) {
            LineString geometry = edge.getGeometry();
            if (geometry != null) {
                if (coordinates.size() == 0) {
                    coordinates.extend(geometry.getCoordinates());
                } else {
                    coordinates.extend(geometry.getCoordinates(), 1); // Avoid duplications
                }
            }
        }
        Geometry geom = GeometryUtils.getGeometryFactory().createLineString(coordinates);
        this.geometry = PolylineEncoder.createEncodings(geom);
        Itinerary itin = GraphPathToTripPlanConverter.generateItinerary(path, false);
        for (Leg leg : itin.legs) {
            walkSteps.addAll(leg.walkSteps);
            // populate the streetEdges array
            for(WalkStep walkStep : leg.walkSteps) {
                boolean firstEdge = true;
                for(Edge edge : walkStep.edges) {
                    StreetEdgeInfo edgeInfo = new StreetEdgeInfo(edge);
                    if(firstEdge) {
                        edgeInfo.mode = walkStep.newMode;
                        edgeInfo.streetName = walkStep.streetName;
                        edgeInfo.absoluteDirection = walkStep.absoluteDirection;
                        edgeInfo.relativeDirection = walkStep.relativeDirection;
                        edgeInfo.stayOn = walkStep.stayOn;
                        edgeInfo.area = walkStep.area;
                        edgeInfo.bogusName = walkStep.bogusName;
                        firstEdge = false;
                    }
                    streetEdges.add(edgeInfo);
                }
            }
        }
        time = (int) (state.getElapsedTimeSeconds());
    }

    /** A StreetSegment is very similar to a StopAtDistance but it's a response object so the State has to be rendered into walksteps. */
    public StreetSegment (StopAtDistance sd) {
        this(sd.state);
        qmode = sd.qmode; // Intended mode is known more reliably in a StopAtDistance than from a State.
    }

    /** Make a collections of StreetSegments from a collection of StopAtDistance. */
    public static List<StreetSegment> list(Collection<StopAtDistance> sds) {
        if (sds == null || sds.isEmpty()) return null;
        List<StreetSegment> ret = Lists.newArrayList();
        for (StopAtDistance sd : sds) {
            ret.add(new StreetSegment(sd));
        }
        return ret;
    }

}